package com.wuxx.diagnosis.knowledge.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeSearchRequest {
    @NotBlank
    private String question;
    private String diagnoseType;
    private String appId;
    private String env;
    private Integer topK;
}
