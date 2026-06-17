package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DiagnoseReport {

    private Long id;

    private String taskNo;

    private String reportTitle;

    private String reportMarkdown;

    private String reportJson;

    private String aiModel;

    private String promptVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
