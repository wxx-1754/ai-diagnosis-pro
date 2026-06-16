package com.wuxx.diagnosis.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiagnoseTaskCreateRequest {

    @NotBlank(message = "appId不能为空")
    private String appId;

    @NotBlank(message = "env不能为空")
    private String env;

    private String userId;

    private String question;

    private String diagnoseType;

    private String targetUri;

    private String targetClass;

    private String targetMethod;
}
