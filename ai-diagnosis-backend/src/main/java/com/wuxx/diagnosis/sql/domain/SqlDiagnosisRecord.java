package com.wuxx.diagnosis.sql.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SqlDiagnosisRecord {

    private Long id;
    private String taskNo;
    private String datasourceCode;
    private String dbType;
    private String mainTableName;
    private String sqlHash;
    private String originalSql;
    private String normalizedSql;
    private String explainSql;
    private String explainResult;
    private String tableMetaJson;
    private String indexMetaJson;
    private String tableStatsJson;
    private String diagnosisResult;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
