package com.wuxx.diagnosis.domain.ai;

import lombok.Data;

@Data
public class DiagnoseIntentResult {

    private String diagnoseType;

    private Double confidence;

    private String reason;

    private String targetClass;

    private String targetMethod;
}
