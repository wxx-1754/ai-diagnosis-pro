package com.wuxx.diagnosis.domain.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentDiagnoseStartResponse {

    private String taskNo;

    private String status;

    private String streamUrl;
}
