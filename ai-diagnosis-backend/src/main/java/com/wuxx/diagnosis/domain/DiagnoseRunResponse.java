package com.wuxx.diagnosis.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnoseRunResponse {

    private String taskNo;

    private String status;

    private String conclusion;

    private List<ArthasExecuteResponse> commandResults;
}
