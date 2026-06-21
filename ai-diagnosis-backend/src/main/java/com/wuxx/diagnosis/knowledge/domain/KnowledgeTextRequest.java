package com.wuxx.diagnosis.knowledge.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeTextRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    private String category;
    private String diagnoseType;
    private String appId;
    private String env;
    private String sourceRef;
    private String uploadedBy;
}
