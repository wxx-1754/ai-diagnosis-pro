package com.wuxx.diagnosis.sql.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SqlDatasourceUpsertRequest {

    @NotBlank(message = "datasourceCode不能为空")
    private String datasourceCode;

    @NotBlank(message = "datasourceName不能为空")
    private String datasourceName;

    @NotBlank(message = "jdbcUrl不能为空")
    private String jdbcUrl;

    @NotBlank(message = "username不能为空")
    private String username;

    private String password;

    @NotBlank(message = "env不能为空")
    private String env;

    private String status = "ENABLED";
}
