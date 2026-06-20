package com.wuxx.diagnosis.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.mapper.DiagnoseEventMapper;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DiagnoseEventService {

    private final DiagnoseEventMapper diagnoseEventMapper;

    private final ObjectMapper objectMapper;

    public DiagnoseEvent save(DiagnoseEvent event) {
        if (event.getTime() == null) {
            event.setTime(LocalDateTime.now());
        }
        event.setMessage(truncate(event.getMessage(), 1024));
        event.setCommand(truncate(event.getCommand(), 512));
        event.setToolName(truncate(event.getToolName(), 128));
        event.setDataJson(serialize(event.getData()));
        diagnoseEventMapper.insert(event);
        return event;
    }

    public List<DiagnoseEvent> findAfter(String taskNo, long afterEventId) {
        List<DiagnoseEvent> events = diagnoseEventMapper.findAfter(taskNo, Math.max(0, afterEventId));
        if (events == null) {
            return Collections.emptyList();
        }
        events.forEach(this::hydrateData);
        return events;
    }

    public Long findLastEventId(String taskNo) {
        return diagnoseEventMapper.findLastEventId(taskNo);
    }

    public LocalDateTime findLastEventTime(String taskNo) {
        return diagnoseEventMapper.findLastEventTime(taskNo);
    }

    public String findExecutionMode(String taskNo) {
        DiagnoseEvent event = diagnoseEventMapper.findLatestByType(taskNo, DiagnoseEventType.PLAN_CREATED.name());
        if (event == null || !StringUtils.hasText(event.getDataJson())) {
            return "TOOL_CALLING";
        }
        hydrateData(event);
        return event.getData() instanceof String mode && StringUtils.hasText(mode)
                ? mode
                : "TOOL_CALLING";
    }

    public void deleteByTaskNo(String taskNo) {
        diagnoseEventMapper.deleteByTaskNo(taskNo);
    }

    private String serialize(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("诊断事件序列化失败", exception);
        }
    }

    private void hydrateData(DiagnoseEvent event) {
        if (!StringUtils.hasText(event.getDataJson())) {
            event.setData(null);
            return;
        }
        try {
            event.setData(objectMapper.readValue(event.getDataJson(), Object.class));
        } catch (JsonProcessingException exception) {
            event.setData(event.getDataJson());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
