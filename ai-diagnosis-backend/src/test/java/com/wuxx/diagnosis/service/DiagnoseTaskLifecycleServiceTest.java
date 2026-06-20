package com.wuxx.diagnosis.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import org.junit.jupiter.api.Test;

class DiagnoseTaskLifecycleServiceTest {

    @Test
    void interruptsOrphanedActiveTaskImmediatelyAfterRestart() {
        DiagnoseTask task = task("DIAG-ORPHANED");
        DiagnoseTaskMapper mapper = mock(DiagnoseTaskMapper.class);
        DiagnoseTaskService taskService = mock(DiagnoseTaskService.class);
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        TaskExecutionRegistry registry = mock(TaskExecutionRegistry.class);
        DiagnoseSseManager sseManager = mock(DiagnoseSseManager.class);
        when(mapper.findActiveTasks()).thenReturn(List.of(task));
        when(taskService.markInterrupted(eq(task.getTaskNo()), contains("后台服务已重启"))).thenReturn(true);

        new DiagnoseTaskLifecycleService(mapper, taskService, eventService, registry, sseManager)
                .reconcileTasksAfterRestart();

        verify(taskService).markInterrupted(eq(task.getTaskNo()), contains("后台服务已重启"));
        verify(sseManager).send(eq(task.getTaskNo()), org.mockito.ArgumentMatchers.any());
        verify(sseManager).complete(task.getTaskNo());
        verify(eventService, never()).findLastEventTime(task.getTaskNo());
    }

    @Test
    void keepsRegisteredTaskActiveDuringStartupReconciliation() {
        DiagnoseTask task = task("DIAG-STARTED-DURING-BOOT");
        DiagnoseTaskMapper mapper = mock(DiagnoseTaskMapper.class);
        DiagnoseTaskService taskService = mock(DiagnoseTaskService.class);
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        TaskExecutionRegistry registry = mock(TaskExecutionRegistry.class);
        DiagnoseSseManager sseManager = mock(DiagnoseSseManager.class);
        when(mapper.findActiveTasks()).thenReturn(List.of(task));
        when(registry.isActive(task.getTaskNo())).thenReturn(true);

        new DiagnoseTaskLifecycleService(mapper, taskService, eventService, registry, sseManager)
                .reconcileTasksAfterRestart();

        verify(taskService, never()).markInterrupted(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sseManager, never()).complete(task.getTaskNo());
    }

    @Test
    void interruptsInactiveTaskWithoutProgressForFiveMinutes() {
        DiagnoseTask task = task("DIAG-STALE");
        DiagnoseTaskMapper mapper = mock(DiagnoseTaskMapper.class);
        DiagnoseTaskService taskService = mock(DiagnoseTaskService.class);
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        TaskExecutionRegistry registry = mock(TaskExecutionRegistry.class);
        DiagnoseSseManager sseManager = mock(DiagnoseSseManager.class);
        when(mapper.findActiveTasks()).thenReturn(List.of(task));
        when(eventService.findLastEventTime(task.getTaskNo()))
                .thenReturn(LocalDateTime.now().minusMinutes(6));
        when(taskService.markInterrupted(eq(task.getTaskNo()), contains("5 分钟"))).thenReturn(true);

        new DiagnoseTaskLifecycleService(mapper, taskService, eventService, registry, sseManager)
                .reconcileStaleTasks();

        verify(taskService).markInterrupted(eq(task.getTaskNo()), contains("5 分钟"));
        verify(sseManager).send(eq(task.getTaskNo()), org.mockito.ArgumentMatchers.any());
        verify(sseManager).complete(task.getTaskNo());
    }

    @Test
    void keepsRegisteredTaskActiveEvenWhenLastEventIsOld() {
        DiagnoseTask task = task("DIAG-ACTIVE");
        DiagnoseTaskMapper mapper = mock(DiagnoseTaskMapper.class);
        DiagnoseTaskService taskService = mock(DiagnoseTaskService.class);
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        TaskExecutionRegistry registry = mock(TaskExecutionRegistry.class);
        DiagnoseSseManager sseManager = mock(DiagnoseSseManager.class);
        when(mapper.findActiveTasks()).thenReturn(List.of(task));
        when(registry.isActive(task.getTaskNo())).thenReturn(true);

        new DiagnoseTaskLifecycleService(mapper, taskService, eventService, registry, sseManager)
                .reconcileStaleTasks();

        verify(taskService, never()).markInterrupted(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sseManager, never()).complete(task.getTaskNo());
    }

    private DiagnoseTask task(String taskNo) {
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo(taskNo);
        task.setStatus(DiagnoseTaskStatus.RUNNING.name());
        task.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        task.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        return task;
    }
}
