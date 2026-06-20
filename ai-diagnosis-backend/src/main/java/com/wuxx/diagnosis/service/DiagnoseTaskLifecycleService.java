package com.wuxx.diagnosis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnoseTaskLifecycleService {

    static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private static final String INTERRUPTED_REASON = "后台诊断执行已不存在，任务超过 5 分钟没有进展";

    private static final String RESTART_INTERRUPTED_REASON = "后台服务已重启，原诊断执行器已不存在";

    private final DiagnoseTaskMapper diagnoseTaskMapper;

    private final DiagnoseTaskService diagnoseTaskService;

    private final DiagnoseEventService diagnoseEventService;

    private final TaskExecutionRegistry executionRegistry;

    private final DiagnoseSseManager sseManager;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileTasksAfterRestart() {
        List<DiagnoseTask> tasks = diagnoseTaskMapper.findActiveTasks();
        if (tasks == null) {
            return;
        }
        tasks.stream()
                .filter(this::isRunningState)
                .filter(task -> !executionRegistry.isActive(task.getTaskNo()))
                .forEach(task -> interrupt(task, RESTART_INTERRUPTED_REASON));
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void reconcileStaleTasks() {
        List<DiagnoseTask> tasks = diagnoseTaskMapper.findActiveTasks();
        if (tasks == null) {
            return;
        }
        tasks.forEach(this::reconcile);
    }

    public DiagnoseTask reconcile(String taskNo) {
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        reconcile(task);
        return diagnoseTaskService.getByTaskNo(taskNo);
    }

    private void reconcile(DiagnoseTask task) {
        if (!isRunningState(task) || executionRegistry.isActive(task.getTaskNo())) {
            return;
        }
        LocalDateTime lastProgress = diagnoseEventService.findLastEventTime(task.getTaskNo());
        if (lastProgress == null) {
            lastProgress = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        }
        if (lastProgress == null || lastProgress.isAfter(LocalDateTime.now().minus(STALE_THRESHOLD))) {
            return;
        }
        interrupt(task, INTERRUPTED_REASON);
    }

    private void interrupt(DiagnoseTask task, String reason) {
        if (!diagnoseTaskService.markInterrupted(task.getTaskNo(), reason)) {
            return;
        }
        log.warn("Marked diagnosis task interrupted, taskNo={}, reason={}", task.getTaskNo(), reason);
        sseManager.send(task.getTaskNo(), DiagnoseEvent.builder()
                .taskNo(task.getTaskNo())
                .eventType(DiagnoseEventType.TASK_INTERRUPTED.name())
                .message(reason)
                .success(false)
                .time(LocalDateTime.now())
                .build());
        sseManager.complete(task.getTaskNo());
    }

    private boolean isRunningState(DiagnoseTask task) {
        return DiagnoseTaskStatus.CREATED.name().equals(task.getStatus())
                || DiagnoseTaskStatus.RUNNING.name().equals(task.getStatus());
    }
}
