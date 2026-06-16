package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ArthasCommandRecord {

    private Long id;

    private String requestNo;

    private String taskNo;

    private String appId;

    private String env;

    private String command;

    private String commandType;

    private Boolean success;

    private Long costMillis;

    private String outputExcerpt;

    private String errorMessage;

    private LocalDateTime createdAt;
}
