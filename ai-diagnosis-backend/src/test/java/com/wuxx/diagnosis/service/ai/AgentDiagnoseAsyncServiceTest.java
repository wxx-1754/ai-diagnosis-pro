package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.service.DiagnoseEventService;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.service.TaskExecutionRegistry;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentDiagnoseAsyncServiceTest {

    @Test
    void restartCreatesNewTaskFromInterruptedTask() {
        DiagnoseTaskService taskService = mock(DiagnoseTaskService.class);
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        TaskExecutionRegistry registry = mock(TaskExecutionRegistry.class);
        DiagnoseSseManager sseManager = mock(DiagnoseSseManager.class);
        DiagnoseTask source = sourceTask(DiagnoseTaskStatus.INTERRUPTED.name());
        when(taskService.getByTaskNo("OLD")).thenReturn(source);
        when(taskService.createTask(any())).thenReturn(DiagnoseTaskCreateResponse.builder()
                .taskNo("NEW")
                .status(DiagnoseTaskStatus.CREATED.name())
                .build());
        when(eventService.findExecutionMode("OLD")).thenReturn("RULE_FIRST");
        AgentDiagnoseAsyncService service = service(taskService, eventService, registry, sseManager);

        assertThat(service.restart("OLD")).isEqualTo("NEW");

        ArgumentCaptor<DiagnoseTaskCreateRequest> captor = ArgumentCaptor.forClass(DiagnoseTaskCreateRequest.class);
        verify(taskService).createTask(captor.capture());
        assertThat(captor.getValue())
                .extracting(DiagnoseTaskCreateRequest::getAppId,
                        DiagnoseTaskCreateRequest::getEnv,
                        DiagnoseTaskCreateRequest::getQuestion,
                        DiagnoseTaskCreateRequest::getTargetUri)
                .containsExactly("order-service", "test", "CPU 持续升高", "/orders");
        verify(eventService).findExecutionMode("OLD");
        verify(registry).register("NEW");
    }

    @Test
    void restartRejectsNonInterruptedTask() {
        DiagnoseTaskService taskService = mock(DiagnoseTaskService.class);
        when(taskService.getByTaskNo("RUNNING")).thenReturn(sourceTask(DiagnoseTaskStatus.RUNNING.name()));
        AgentDiagnoseAsyncService service = service(
                taskService,
                mock(DiagnoseEventService.class),
                mock(TaskExecutionRegistry.class),
                mock(DiagnoseSseManager.class)
        );

        assertThatThrownBy(() -> service.restart("RUNNING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERRUPTED");
    }

    private AgentDiagnoseAsyncService service(DiagnoseTaskService taskService,
                                              DiagnoseEventService eventService,
                                              TaskExecutionRegistry registry,
                                              DiagnoseSseManager sseManager) {
        Executor queuedExecutor = command -> {
            // Deliberately leave the task queued so this unit test only verifies restart submission.
        };
        return new AgentDiagnoseAsyncService(
                taskService,
                mock(DiagnoseIntentClassifier.class),
                mock(HybridDiagnosisExecutor.class),
                sseManager,
                queuedExecutor,
                mock(DiagnosisReportGenerator.class),
                mock(DiagnoseReportService.class),
                mock(ArthasCommandRecordMapper.class),
                mock(DiagnosisAiProperties.class),
                mock(DiagnosisInsightSummarizer.class),
                new ObjectMapper(),
                registry,
                eventService,
                mock(com.wuxx.diagnosis.sql.mapper.SqlDiagnosisRecordMapper.class),
                mock(com.wuxx.diagnosis.sql.ai.JavaSqlJointReportGenerator.class),
                mock(com.wuxx.diagnosis.sql.security.SqlSensitiveDataMasker.class)
        );
    }

    private DiagnoseTask sourceTask(String status) {
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo("OLD");
        task.setAppId("order-service");
        task.setEnv("test");
        task.setUserId("admin");
        task.setQuestion("CPU 持续升高");
        task.setTargetUri("/orders");
        task.setTargetClass("com.example.OrderController");
        task.setTargetMethod("create");
        task.setStatus(status);
        return task;
    }
}
