package com.wuxx.diagnosis.sql.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SqlDatasourceUpsertRequest {

    @NotBlank(message = "datasourceCode不能为空")
    private String datasourceCode;

    @NotBlank(message = "datasourceName不能为空")
    private String datasourceName;

    @NotBlank(message = "appId不能为空")
    private String appId;

    /**
     * 新建时必填；更新时允许留空，表示保留数据库中的原始 JDBC URL。
     * 不能使用列表接口返回的 jdbcUrlMasked 回写。
     */
    private String jdbcUrl;

    @NotBlank(message = "username不能为空")
    private String username;

    private String password;

    @NotBlank(message = "env不能为空")
    private String env;

    private String status = "ENABLED";
}
