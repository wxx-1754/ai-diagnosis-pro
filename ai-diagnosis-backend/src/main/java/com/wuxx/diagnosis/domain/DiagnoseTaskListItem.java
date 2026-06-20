package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 概览列表中的单条诊断事件摘要，只携带列表所需字段，
 * question / conclusion 在 SQL 侧截断，避免拉取大字段。
 */
@Data
public class DiagnoseTaskListItem {

    private String taskNo;

    private String appId;

    private String env;

    private String diagnoseType;

    private String status;

    private String question;

    private String targetUri;

    private String targetClass;

    private String targetMethod;

    private String conclusion;

    private String errorMessage;

    private String userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
