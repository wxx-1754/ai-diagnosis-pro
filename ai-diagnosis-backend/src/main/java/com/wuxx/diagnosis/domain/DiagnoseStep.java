package com.wuxx.diagnosis.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnoseStep {

    private Integer stepNo;

    private String commandType;

    private String command;

    private String purpose;
}
