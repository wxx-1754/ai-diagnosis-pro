package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DiagnoseTask {

    private Long id;

    private String taskNo;

    private String appId;

    private String env;

    private String userId;

    private String question;

    private String diagnoseType;

    private String targetUri;

    private String targetClass;

    private String targetMethod;

    private String status;

    private String conclusion;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
