package com.wuxx.diagnosis.sql.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JavaSqlJointDiagnosisRequest {

    @NotBlank(message = "taskNo不能为空")
    private String taskNo;

    @NotBlank(message = "datasourceCode不能为空")
    private String datasourceCode;

    @NotBlank(message = "sql不能为空")
    private String sql;

    @NotBlank(message = "mainTableName不能为空")
    private String mainTableName;
}
