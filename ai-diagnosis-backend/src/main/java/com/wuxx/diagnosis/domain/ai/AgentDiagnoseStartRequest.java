package com.wuxx.diagnosis.domain.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentDiagnoseStartRequest {

    @NotBlank(message = "appId不能为空")
    private String appId;

    @NotBlank(message = "env不能为空")
    private String env;

    private String userId;

    @NotBlank(message = "question不能为空")
    private String question;

    private String targetClass;

    private String targetMethod;

    private String targetUri;

    private String mode;
}
