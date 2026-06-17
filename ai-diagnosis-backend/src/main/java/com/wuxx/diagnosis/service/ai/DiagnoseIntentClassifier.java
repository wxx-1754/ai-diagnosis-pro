package com.wuxx.diagnosis.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.domain.ai.DiagnoseIntentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class DiagnoseIntentClassifier {

    private static final String SYSTEM_PROMPT = """
            你是一个 Java 应用生产问题诊断助手。

            你的任务是根据用户输入，判断问题属于以下哪一种诊断类型：

            1. HIGH_CPU：CPU 高、负载高、Java 进程占用 CPU 高、线程占用 CPU 高。
            2. MEMORY_ABNORMAL：内存上涨、堆内存高、频繁 GC、Full GC、Metaspace、Direct Memory 异常。
            3. THREAD_BLOCKED：线程阻塞、请求卡住、死锁、线程池耗尽、连接池等待、服务无响应。
            4. SLOW_REQUEST：接口慢、方法慢、请求耗时高，需要 trace 指定类和方法。
            5. UNKNOWN：无法判断。

            要求：
            1. 只能输出 JSON。
            2. diagnoseType 必须是上述枚举之一。
            3. confidence 范围是 0 到 1。
            4. 不确定时输出 UNKNOWN。
            5. 如果用户明确提供 Java 全限定类名和方法名，可以提取 targetClass 和 targetMethod。
            6. 不要编造类名和方法名。
            7. 不要输出 Arthas 命令。
            """;

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    private final DiagnosisAiProperties properties;

    public DiagnoseIntentResult classify(String question, String targetClass, String targetMethod) {
        ensureEnabled();
        String response = chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .temperature(properties.getIntentTemperature())
                        .build())
                .system(SYSTEM_PROMPT)
                .user("""
                        用户问题：
                        %s

                        用户提供的目标类：
                        %s

                        用户提供的目标方法：
                        %s

                        请输出 JSON：
                        {
                          "diagnoseType": "HIGH_CPU | MEMORY_ABNORMAL | THREAD_BLOCKED | SLOW_REQUEST | UNKNOWN",
                          "confidence": 0.0,
                          "reason": "识别原因",
                          "targetClass": "可为空",
                          "targetMethod": "可为空"
                        }
                        """.formatted(question, nullToEmpty(targetClass), nullToEmpty(targetMethod)))
                .call()
                .content();

        return parseAndValidate(response);
    }

    DiagnoseIntentResult parseAndValidate(String response) {
        try {
            DiagnoseIntentResult result = objectMapper.readValue(cleanupJson(response), DiagnoseIntentResult.class);
            if (result == null) {
                return fallback("AI 返回为空");
            }
            normalize(result);
            return result;
        } catch (Exception exception) {
            return fallback("AI 返回解析失败：" + exception.getMessage());
        }
    }

    private DiagnoseIntentResult fallback(String reason) {
        DiagnoseIntentResult fallback = new DiagnoseIntentResult();
        fallback.setDiagnoseType(DiagnoseType.UNKNOWN.name());
        fallback.setConfidence(0.0);
        fallback.setReason(reason);
        return fallback;
    }

    private void normalize(DiagnoseIntentResult result) {
        if (result == null) {
            return;
        }
        if (isValidType(result.getDiagnoseType())) {
            result.setDiagnoseType(DiagnoseType.valueOf(result.getDiagnoseType().trim().toUpperCase()).name());
        } else {
            result.setDiagnoseType(DiagnoseType.UNKNOWN.name());
        }
        if (result.getConfidence() == null) {
            result.setConfidence(0.0);
        }
        result.setConfidence(Math.max(0.0, Math.min(1.0, result.getConfidence())));
        result.setTargetClass(blankToNull(result.getTargetClass()));
        result.setTargetMethod(blankToNull(result.getTargetMethod()));
    }

    private String cleanupJson(String response) {
        if (!StringUtils.hasText(response)) {
            return "{}";
        }

        String text = response.trim();
        if (text.startsWith("```json")) {
            text = text.substring("```json".length()).trim();
        }
        if (text.startsWith("```")) {
            text = text.substring("```".length()).trim();
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3).trim();
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private boolean isValidType(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        try {
            DiagnoseType diagnoseType = DiagnoseType.valueOf(type.trim().toUpperCase());
            return diagnoseType == DiagnoseType.HIGH_CPU
                    || diagnoseType == DiagnoseType.MEMORY_ABNORMAL
                    || diagnoseType == DiagnoseType.THREAD_BLOCKED
                    || diagnoseType == DiagnoseType.SLOW_REQUEST
                    || diagnoseType == DiagnoseType.UNKNOWN;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnable()) {
            throw new IllegalStateException("AI 诊断未开启，请设置 diagnosis.ai.enable=true 并配置模型访问参数");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
