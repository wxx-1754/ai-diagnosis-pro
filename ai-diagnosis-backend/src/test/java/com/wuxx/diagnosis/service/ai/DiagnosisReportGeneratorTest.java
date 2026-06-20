package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import org.junit.jupiter.api.Test;

class DiagnosisReportGeneratorTest {

    @Test
    void buildArthasContextMasksSensitiveDataAndLimitsOutput() {
        DiagnosisAiProperties properties = new DiagnosisAiProperties();
        properties.setPerCommandOutputLimit(60);
        properties.setMaxArthasOutputLength(180);
        DiagnosisReportGenerator generator = new DiagnosisReportGenerator(null, properties);

        ArthasCommandRecord record = new ArthasCommandRecord();
        record.setCommandType("dashboard");
        record.setCommand("dashboard -n 1");
        record.setSuccess(true);
        record.setCostMillis(12L);
        record.setOutputExcerpt("Authorization: Bearer abc123\nemail=user@example.com phone=13800138000 " + "x".repeat(120));
        record.setCreatedAt(LocalDateTime.now());

        String context = generator.buildArthasContext(List.of(record));

        assertThat(context).contains("commandType: dashboard");
        assertThat(context).contains("Authorization=******");
        assertThat(context).contains("******@******");
        assertThat(context).doesNotContain("abc123");
        assertThat(context).doesNotContain("Bearer");
        assertThat(context).doesNotContain("user@example.com");
        assertThat(context).contains("输出过长，已截断");
    }

    @Test
    void reportPromptRequiresRootCauseEffectAndDetailedActions() {
        DiagnosisAiProperties properties = new DiagnosisAiProperties();
        DiagnosisReportGenerator generator = new DiagnosisReportGenerator(null, properties);
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo("DIAG-001");
        task.setAppId("order-service");
        task.setEnv("prod");
        task.setQuestion("订单接口变慢");
        task.setDiagnoseType("SLOW_REQUEST");

        String prompt = generator.buildReportPrompt(task, "trace output");

        assertThat(prompt)
                .contains("## 5. 根因分析")
                .contains("## 6. 预期效果")
                .contains("## 7. 推荐操作")
                .contains("## 8. 风险提示")
                .contains("## 9. 结论摘要");
    }
}
