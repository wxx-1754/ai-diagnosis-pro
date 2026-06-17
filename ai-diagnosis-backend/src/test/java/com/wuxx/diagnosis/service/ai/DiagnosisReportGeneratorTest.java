package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
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
}
