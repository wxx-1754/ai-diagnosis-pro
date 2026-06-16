package com.wuxx.diagnosis.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArthasExecuteRequest {

    @NotBlank(message = "appId不能为空")
    private String appId;

    @NotBlank(message = "env不能为空")
    private String env;

    @NotBlank(message = "commandType不能为空")
    private String commandType;

}
