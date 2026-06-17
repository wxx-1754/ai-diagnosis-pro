package com.wuxx.diagnosis.service.ai;

import java.util.List;
import java.util.stream.Collectors;

import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class DiagnosisReportGenerator {

    private static final String SYSTEM_PROMPT = """
            你是一个资深 Java 生产问题诊断专家，熟悉 Arthas、JVM、线程、GC、接口耗时分析。

            你需要根据系统提供的诊断任务信息和 Arthas 命令输出生成诊断报告。

            必须遵守：
            1. 只能基于提供的 Arthas 输出进行分析。
            2. 不允许编造没有采集到的数据。
            3. 如果证据不足，必须明确说明“当前证据不足，需要进一步确认”。
            4. 不允许建议用户直接执行 ognl、redefine、retransform、dump、jad、shutdown、stop 等高风险命令。
            5. heapdump 属于高风险操作，只能建议在低峰期、审批后、确认磁盘空间充足时人工执行。
            6. 输出必须结构化。
            7. 建议优先给出低风险排查手段。
            8. 如果发现问题可能与 SQL、Redis、RPC、HTTP 下游调用有关，需要说明需要结合对应系统进一步确认。
            """;

    private final ChatClient chatClient;

    private final DiagnosisAiProperties properties;

    public String generateMarkdownReport(DiagnoseTask task, List<ArthasCommandRecord> records) {
        ensureEnabled();
        String arthasOutputs = buildArthasContext(records);

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
                        目标类: %s
                        目标方法: %s

                        Arthas 命令执行结果：
                        %s

                        请生成一份 Markdown 诊断报告，结构如下：

                        # Java 应用智能诊断报告

                        ## 1. 问题现象
                        ## 2. 诊断类型
                        ## 3. 执行步骤
                        ## 4. 关键发现
                        ## 5. 初步判断
                        ## 6. 建议方案
                        ## 7. 风险提示
                        ## 8. 后续建议
                        ## 9. 结论摘要
                        """.formatted(
                        task.getTaskNo(),
                        task.getAppId(),
                        task.getEnv(),
                        nullToEmpty(task.getQuestion()),
                        nullToEmpty(task.getDiagnoseType()),
                        nullToEmpty(task.getTargetClass()),
                        nullToEmpty(task.getTargetMethod()),
                        arthasOutputs
                ))
                .call()
                .content();
    }

    String buildArthasContext(List<ArthasCommandRecord> records) {
        if (records == null || records.isEmpty()) {
            return "当前没有 Arthas 命令输出。";
        }

        String context = records.stream()
                .map(this::formatRecord)
                .collect(Collectors.joining("\n"));

        if (context.length() <= properties.getMaxArthasOutputLength()) {
            return context;
        }

        return context.substring(0, properties.getMaxArthasOutputLength())
                + "\n... Arthas 输出整体过长，已截断 ...";
    }

    private String formatRecord(ArthasCommandRecord record) {
        return """
                ----
                commandType: %s
                command: %s
                success: %s
                costMillis: %s
                output:
                %s
                error:
                %s
                """.formatted(
                nullToEmpty(record.getCommandType()),
                nullToEmpty(record.getCommand()),
                record.getSuccess(),
                record.getCostMillis(),
                limitAndMask(record.getOutputExcerpt(), properties.getPerCommandOutputLimit()),
                limitAndMask(record.getErrorMessage(), properties.getPerCommandOutputLimit())
        );
    }

    private String limitAndMask(String text, int maxLength) {
        String masked = SensitiveDataMasker.mask(text);
        if (masked == null || maxLength <= 0 || masked.length() <= maxLength) {
            return nullToEmpty(masked);
        }
        return masked.substring(0, maxLength) + "\n... 输出过长，已截断 ...";
    }

    private void ensureEnabled() {
        if (!properties.isEnable()) {
            throw new IllegalStateException("AI 诊断未开启，请设置 diagnosis.ai.enable=true 并配置模型访问参数");
        }
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
