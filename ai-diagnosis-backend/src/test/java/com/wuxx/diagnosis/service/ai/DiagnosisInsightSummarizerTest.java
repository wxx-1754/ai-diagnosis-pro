package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ai.DiagnosisInsightSummary;
import org.junit.jupiter.api.Test;

class DiagnosisInsightSummarizerTest {

    @Test
    void parsesCleansAndLimitsInsightJson() {
        DiagnosisInsightSummarizer summarizer = new DiagnosisInsightSummarizer(
                null,
                new ObjectMapper(),
                new DiagnosisAiProperties()
        );

        DiagnosisInsightSummary summary = summarizer.parseAndNormalize("""
                ```json
                {
                  "rootCause": "**Mapper 重复查询数据库导致接口耗时升高。**",
                  "specificReasons": [
                    "1. trace 显示 Mapper 占总耗时 82%。",
                    "2. 同一请求内重复执行相同查询。",
                    "3. 数据库等待时间明显高于业务计算。",
                    "4. 不应展示的第四条原因"
                  ],
                  "expectedEffect": "预计 P95 可下降 20%–30%，最终需压测验证。",
                  "recommendedActions": [
                    "1. 合并重复查询并增加单元测试。",
                    "2. 灰度发布后观察 P95 和错误率。",
                    "3. 未达预期时回滚并补充 trace 证据。",
                    "4. 不应展示的第四条操作"
                  ]
                }
                ```
                """);

        assertThat(summary.getRootCause()).isEqualTo("Mapper 重复查询数据库导致接口耗时升高。");
        assertThat(summary.getSpecificReasons()).hasSize(3);
        assertThat(summary.getSpecificReasons()).allMatch(reason -> reason.length() <= 56);
        assertThat(summary.getExpectedEffect()).contains("P95");
        assertThat(summary.getRecommendedActions()).hasSize(3);
        assertThat(summary.getRecommendedActions()).allMatch(action -> action.length() <= 56);
    }

    @Test
    void returnsShortFallbackWhenAiOutputCannotBeParsed() {
        DiagnosisInsightSummarizer summarizer = new DiagnosisInsightSummarizer(
                null,
                new ObjectMapper(),
                new DiagnosisAiProperties()
        );

        DiagnosisInsightSummary summary = summarizer.parseAndNormalize("not-json");

        assertThat(summary.getRootCause()).hasSizeLessThanOrEqualTo(72);
        assertThat(summary.getSpecificReasons()).hasSize(1);
        assertThat(summary.getRecommendedActions()).isEqualTo(List.of("下载完整诊断报告并人工复核。"));
    }
}
