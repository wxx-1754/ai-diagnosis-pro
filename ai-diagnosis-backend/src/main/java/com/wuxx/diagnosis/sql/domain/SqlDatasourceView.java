package com.wuxx.diagnosis.sql.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SqlDatasourceView {

    private Long id;
    private String datasourceCode;
    private String datasourceName;
    private String appId;
    private String dbType;
    private String jdbcUrlMasked;
    private String username;
    private String env;
    private String status;
    private boolean passwordConfigured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
