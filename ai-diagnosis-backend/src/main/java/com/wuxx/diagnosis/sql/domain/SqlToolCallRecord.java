package com.wuxx.diagnosis.sql.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SqlToolCallRecord {

    private Long id;
    private String taskNo;
    private Long sqlRecordId;
    private String datasourceCode;
    private String toolName;
    private String requestSql;
    private Boolean success;
    private Long costMillis;
    private String resultExcerpt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
