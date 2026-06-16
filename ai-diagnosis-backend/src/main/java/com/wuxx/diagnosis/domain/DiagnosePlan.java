package com.wuxx.diagnosis.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosePlan {

    private String taskNo;

    private String appId;

    private String env;

    private String diagnoseType;

    private List<DiagnoseStep> steps;
}
