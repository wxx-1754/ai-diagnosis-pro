package com.wuxx.diagnosis.sse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class DiagnoseSseManager {

    private static final long SSE_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private static final int MAX_HISTORY_SIZE = 100;

    private final ConcurrentHashMap<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, List<DiagnoseEvent>> historyMap = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String taskNo) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitterMap.computeIfAbsent(taskNo, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(taskNo, emitter));
        emitter.onTimeout(() -> remove(taskNo, emitter));
        emitter.onError(error -> remove(taskNo, emitter));

        replayHistory(taskNo, emitter);
        send(taskNo, DiagnoseEvent.builder()
                .taskNo(taskNo)
                .eventType(DiagnoseEventType.HEARTBEAT.name())
                .message("SSE 连接已建立")
                .time(LocalDateTime.now())
                .build());
        return emitter;
    }

    public void send(String taskNo, DiagnoseEvent event) {
        if (taskNo == null || event == null) {
            return;
        }
        remember(taskNo, event);

        List<SseEmitter> emitters = emitterMap.get(taskNo);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendToEmitter(taskNo, emitter, event);
        }
    }

    public void complete(String taskNo) {
        List<SseEmitter> emitters = emitterMap.remove(taskNo);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
    }

    private void replayHistory(String taskNo, SseEmitter emitter) {
        List<DiagnoseEvent> events = historyMap.get(taskNo);
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DiagnoseEvent event : events) {
            sendToEmitter(taskNo, emitter, event);
        }
    }

    private void remember(String taskNo, DiagnoseEvent event) {
        List<DiagnoseEvent> events = historyMap.computeIfAbsent(taskNo, key -> new CopyOnWriteArrayList<>());
        events.add(event);
        while (events.size() > MAX_HISTORY_SIZE) {
            events.remove(0);
        }
    }

    private void sendToEmitter(String taskNo, SseEmitter emitter, DiagnoseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getEventType())
                    .data(event));
        } catch (IOException exception) {
            remove(taskNo, emitter);
        }
    }

    private void remove(String taskNo, SseEmitter emitter) {
        List<SseEmitter> emitters = emitterMap.get(taskNo);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}
