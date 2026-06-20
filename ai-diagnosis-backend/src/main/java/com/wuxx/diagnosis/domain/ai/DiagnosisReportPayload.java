package com.wuxx.diagnosis.domain.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosisReportPayload {

    private String reportMarkdown;

    private DiagnosisInsightSummary insightSummary;
}
