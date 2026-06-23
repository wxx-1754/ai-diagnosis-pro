package com.wuxx.diagnosis.knowledge.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeReviewRequest {

    @NotBlank
    private String action;

    private String title;

    private String content;

    private String category;

    private String diagnoseType;

    private String appId;

    private String env;

    private String comment;

    @NotBlank
    private String reviewedBy;
}

