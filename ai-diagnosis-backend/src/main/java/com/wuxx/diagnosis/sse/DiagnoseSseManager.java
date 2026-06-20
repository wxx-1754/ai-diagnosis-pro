package com.wuxx.diagnosis.sse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.wuxx.diagnosis.service.DiagnoseEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class DiagnoseSseManager {

    private static final long SSE_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Object> taskLocks = new ConcurrentHashMap<>();

    private final DiagnoseEventService diagnoseEventService;

    @Autowired
    public DiagnoseSseManager(DiagnoseEventService diagnoseEventService) {
        this.diagnoseEventService = diagnoseEventService;
    }

    public DiagnoseSseManager() {
        this.diagnoseEventService = null;
    }

    public SseEmitter subscribe(String taskNo, long afterEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitter.onCompletion(() -> remove(taskNo, emitter));
        emitter.onTimeout(() -> remove(taskNo, emitter));
        emitter.onError(error -> remove(taskNo, emitter));

        synchronized (lock(taskNo)) {
            replayHistory(taskNo, afterEventId, emitter);
            emitterMap.computeIfAbsent(taskNo, key -> new CopyOnWriteArrayList<>()).add(emitter);
        }
        sendToEmitter(taskNo, emitter, DiagnoseEvent.builder()
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
        synchronized (lock(taskNo)) {
            if (diagnoseEventService != null) {
                diagnoseEventService.save(event);
            }
            List<SseEmitter> emitters = emitterMap.get(taskNo);
            if (emitters == null || emitters.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : emitters) {
                sendToEmitter(taskNo, emitter, event);
            }
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
        taskLocks.remove(taskNo);
    }

    private void replayHistory(String taskNo, long afterEventId, SseEmitter emitter) {
        if (diagnoseEventService == null) {
            return;
        }
        for (DiagnoseEvent event : diagnoseEventService.findAfter(taskNo, afterEventId)) {
            sendToEmitter(taskNo, emitter, event);
        }
    }

    private Object lock(String taskNo) {
        return taskLocks.computeIfAbsent(taskNo, key -> new Object());
    }

    private void sendToEmitter(String taskNo, SseEmitter emitter, DiagnoseEvent event) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.getEventType())
                    .data(event);
            if (event.getId() != null) {
                builder.id(String.valueOf(event.getId()));
            }
            emitter.send(builder);
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
