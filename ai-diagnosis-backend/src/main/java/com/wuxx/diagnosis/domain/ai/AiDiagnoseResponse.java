package com.wuxx.diagnosis.domain.ai;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiDiagnoseResponse {

    private String taskNo;

    private String diagnoseType;

    private String status;

    private String reportMarkdown;

    private String conclusion;

    private DiagnosisInsightSummary insightSummary;

    private List<ReportKnowledgeReference> references;
}
