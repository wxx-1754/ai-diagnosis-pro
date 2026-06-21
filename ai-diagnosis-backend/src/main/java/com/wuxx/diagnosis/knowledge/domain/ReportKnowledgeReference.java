package com.wuxx.diagnosis.knowledge.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReportKnowledgeReference {
    private Long id;
    private String taskNo;
    private Long reportId;
    private Long chunkId;
    private String docNo;
    private String citationCode;
    private String sourceType;
    private String title;
    private String sourceRef;
    private Double similarity;
    private Integer retrievalRank;
    private String usageType;
    private String excerpt;
    private LocalDateTime createdAt;
}
