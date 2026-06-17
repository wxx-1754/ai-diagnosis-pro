package com.wuxx.diagnosis.service.ai;

import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.tools.ArthasDiagnosticTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class ToolCallingDiagnosisAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个 Java 生产问题诊断 Agent。

            你可以使用系统提供的受控诊断工具收集 JVM 和 Arthas 数据。

            你必须遵守：
            1. 只能调用系统提供的工具。
            2. 不得生成原始 Arthas 命令。
            3. 不得要求执行 ognl、heapdump、watch、redefine、retransform、dump、jad、shutdown、stop 等高风险命令。
            4. 每次诊断优先使用低风险工具。
            5. 如果用户问题是 CPU 高，优先调用 dashboard 和 topThreads。
            6. 如果用户问题是内存异常，优先调用 memoryInfo、dashboard、jvmInfo。
            7. 如果用户问题是线程阻塞，优先调用 dashboard，再查看线程信息。
            8. 如果用户问题是接口慢，只有在提供 className 和 methodName 时才调用 traceMethod。
            9. 如果工具返回信息不足，需要明确说明还需要补充哪些信息。
            10. 输出最终诊断报告时必须基于工具返回结果，不得编造。
            """;

    private final ChatClient chatClient;

    private final ArthasDiagnosticTools arthasDiagnosticTools;

    private final DiagnosisAiProperties properties;

    public String diagnose(DiagnoseTask task) {
        return chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .temperature(properties.getReportTemperature())
                        .build())
                .system(SYSTEM_PROMPT)
                .user("""
                        诊断任务信息：
                        taskNo: %s
                        appId: %s
                        env: %s
                        用户问题: %s
                        诊断类型: %s
                        targetClass: %s
                        targetMethod: %s

                        请根据问题选择合适的诊断工具，并在工具返回结果基础上生成诊断结论。
                        """.formatted(
                        task.getTaskNo(),
                        task.getAppId(),
                        task.getEnv(),
                        nullToEmpty(task.getQuestion()),
                        nullToEmpty(task.getDiagnoseType()),
                        nullToEmpty(task.getTargetClass()),
                        nullToEmpty(task.getTargetMethod())
                ))
                .tools(arthasDiagnosticTools)
                .call()
                .content();
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
