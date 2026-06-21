package com.wuxx.diagnosis.domain.ai;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosisReportPayload {

    private String reportMarkdown;

    private DiagnosisInsightSummary insightSummary;

    private List<ReportKnowledgeReference> references;
}
