package com.wuxx.diagnosis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArthasExecuteResponse {

    private String requestNo;

    private String appId;

    private String env;

    private String command;

    private boolean success;

    private String output;

    private String errorMessage;

    private long costMillis;
}
