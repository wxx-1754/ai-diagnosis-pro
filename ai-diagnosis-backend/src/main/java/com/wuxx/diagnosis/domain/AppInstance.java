package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AppInstance {

    private Long id;

    private String appId;

    private String appName;

    private String env;

    private String ip;

    private Integer arthasHttpPort;

    private String arthasUsername;

    private String arthasPassword;

    private String arthasAgentId;

    private String accessMode;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
