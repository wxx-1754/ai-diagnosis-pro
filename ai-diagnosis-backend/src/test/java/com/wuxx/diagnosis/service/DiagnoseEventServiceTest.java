package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.mapper.DiagnoseEventMapper;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import org.junit.jupiter.api.Test;

class DiagnoseEventServiceTest {

    @Test
    void persistsAndHydratesEventData() {
        InMemoryEventMapper mapper = new InMemoryEventMapper();
        DiagnoseEventService service = new DiagnoseEventService(mapper, new ObjectMapper());

        DiagnoseEvent saved = service.save(DiagnoseEvent.builder()
                .taskNo("DIAG-1")
                .eventType(DiagnoseEventType.PLAN_CREATED.name())
                .data("RULE_FIRST")
                .build());

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(service.findAfter("DIAG-1", 0)).singleElement()
                .extracting(DiagnoseEvent::getData)
                .isEqualTo("RULE_FIRST");
        assertThat(service.findExecutionMode("DIAG-1")).isEqualTo("RULE_FIRST");
    }

    @Test
    void defaultsExecutionModeForLegacyTask() {
        DiagnoseEventService service = new DiagnoseEventService(new InMemoryEventMapper(), new ObjectMapper());

        assertThat(service.findExecutionMode("LEGACY")).isEqualTo("TOOL_CALLING");
    }

    private static class InMemoryEventMapper implements DiagnoseEventMapper {

        private final List<DiagnoseEvent> events = new ArrayList<>();

        @Override
        public int insert(DiagnoseEvent event) {
            event.setId((long) events.size() + 1);
            events.add(event);
            return 1;
        }

        @Override
        public List<DiagnoseEvent> findAfter(String taskNo, long afterEventId) {
            return events.stream()
                    .filter(event -> taskNo.equals(event.getTaskNo()) && event.getId() > afterEventId)
                    .map(this::copy)
                    .toList();
        }

        @Override
        public Long findLastEventId(String taskNo) {
            return events.stream()
                    .filter(event -> taskNo.equals(event.getTaskNo()))
                    .mapToLong(DiagnoseEvent::getId)
                    .max()
                    .stream()
                    .boxed()
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public LocalDateTime findLastEventTime(String taskNo) {
            return events.stream()
                    .filter(event -> taskNo.equals(event.getTaskNo()))
                    .map(DiagnoseEvent::getTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }

        @Override
        public DiagnoseEvent findLatestByType(String taskNo, String eventType) {
            return events.stream()
                    .filter(event -> taskNo.equals(event.getTaskNo()) && eventType.equals(event.getEventType()))
                    .reduce((first, second) -> second)
                    .map(this::copy)
                    .orElse(null);
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            int before = events.size();
            events.removeIf(event -> taskNo.equals(event.getTaskNo()));
            return before - events.size();
        }

        private DiagnoseEvent copy(DiagnoseEvent source) {
            return DiagnoseEvent.builder()
                    .id(source.getId())
                    .taskNo(source.getTaskNo())
                    .eventType(source.getEventType())
                    .message(source.getMessage())
                    .command(source.getCommand())
                    .toolName(source.getToolName())
                    .success(source.getSuccess())
                    .dataJson(source.getDataJson())
                    .time(source.getTime())
                    .build();
        }
    }
}
