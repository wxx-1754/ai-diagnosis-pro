package com.wuxx.diagnosis.sse;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnoseEvent {

    private Long id;

    private String taskNo;

    private String eventType;

    private String message;

    private String command;

    private String toolName;

    private Boolean success;

    private Object data;

    @JsonIgnore
    private String dataJson;

    private LocalDateTime time;
}
