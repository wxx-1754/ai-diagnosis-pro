package com.wuxx.diagnosis.sql.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JavaSqlJointDiagnosisResponse {

    private String taskNo;
    private Long sqlRecordId;
    private String status;
    private String streamUrl;
}
