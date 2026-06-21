package com.wuxx.diagnosis.sql.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SqlDatasourceConfig {

    private Long id;
    private String datasourceCode;
    private String datasourceName;
    private String dbType;
    private String jdbcUrl;
    private String username;
    private String passwordCipher;
    private String env;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
