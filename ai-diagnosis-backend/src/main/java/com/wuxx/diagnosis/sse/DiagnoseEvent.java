package com.wuxx.diagnosis.sse;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnoseEvent {

    private String taskNo;

    private String eventType;

    private String message;

    private String command;

    private String toolName;

    private Boolean success;

    private Object data;

    private LocalDateTime time;
}
