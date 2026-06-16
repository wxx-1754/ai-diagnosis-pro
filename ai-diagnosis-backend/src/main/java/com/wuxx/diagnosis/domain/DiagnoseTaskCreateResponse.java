package com.wuxx.diagnosis.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnoseTaskCreateResponse {

    private String taskNo;

    private String status;
}
