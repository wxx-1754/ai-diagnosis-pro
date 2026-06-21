package com.wuxx.diagnosis.knowledge.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KbDocument {
    private Long id;
    private String docNo;
    private String title;
    private String sourceType;
    private String category;
    private String diagnoseType;
    private String appId;
    private String env;
    private String sourceRef;
    private String contentHash;
    private Integer version;
    private String qualityStatus;
    private String status;
    private Integer chunkCount;
    private Long fileSize;
    private String errorMessage;
    private String embeddingModel;
    private Integer embeddingDimension;
    private String uploadedBy;
    private LocalDateTime indexedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
