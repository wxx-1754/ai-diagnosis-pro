package com.wuxx.diagnosis.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import com.wuxx.diagnosis.service.ArthasCommandService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import org.junit.jupiter.api.Test;

class ArthasDiagnosticToolsTest {

    @Test
    void topThreadsClampsTopNAndDispatchesFixedCommand() {
        CapturingArthasCommandService commandService = new CapturingArthasCommandService();
        ArthasDiagnosticTools tools = tools(commandService, taskMapper(task("DIAG-1", "order-service", "test")),
                new CapturingRecordMapper());

        ArthasExecuteResponse response = tools.topThreads("DIAG-1", "order-service", "test", 99);

        assertThat(response.isSuccess()).isTrue();
        assertThat(commandService.command).isEqualTo("thread -n 10");
        assertThat(commandService.request.getCommandType()).isEqualTo("topThreads");
    }

    @Test
    void traceMethodRejectsInvalidClassNameBeforeDispatch() {
        CapturingArthasCommandService commandService = new CapturingArthasCommandService();
        ArthasDiagnosticTools tools = tools(commandService, taskMapper(task("DIAG-2", "order-service", "test")),
                new CapturingRecordMapper());

        assertThatThrownBy(() -> tools.traceMethod("DIAG-2", "order-service", "test",
                "com.demo.OrderService;shutdown", "createOrder"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("className");
        assertThat(commandService.command).isNull();
    }

    @Test
    void toolCallRejectsTaskAppEnvMismatch() {
        CapturingArthasCommandService commandService = new CapturingArthasCommandService();
        ArthasDiagnosticTools tools = tools(commandService, taskMapper(task("DIAG-3", "order-service", "prod")),
                new CapturingRecordMapper());

        assertThatThrownBy(() -> tools.dashboard("DIAG-3", "order-service", "test"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("诊断任务与目标应用不匹配");
        assertThat(commandService.command).isNull();
    }

    private ArthasDiagnosticTools tools(CapturingArthasCommandService commandService,
                                        DiagnoseTaskMapper taskMapper,
                                        ArthasCommandRecordMapper recordMapper) {
        return new ArthasDiagnosticTools(
                commandService,
                new DiagnoseTaskService(taskMapper, null, null),
                new ToolCallLimiter(recordMapper)
        );
    }

    private CapturingDiagnoseTaskMapper taskMapper(DiagnoseTask task) {
        CapturingDiagnoseTaskMapper mapper = new CapturingDiagnoseTaskMapper();
        mapper.save(task);
        return mapper;
    }

    private DiagnoseTask task(String taskNo, String appId, String env) {
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo(taskNo);
        task.setAppId(appId);
        task.setEnv(env);
        task.setDiagnoseType("HIGH_CPU");
        task.setStatus(DiagnoseTaskStatus.CREATED.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private static class CapturingArthasCommandService extends ArthasCommandService {

        private ArthasExecuteRequest request;

        private String command;

        CapturingArthasCommandService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public ArthasExecuteResponse executeCommand(ArthasExecuteRequest request, String command) {
            this.request = request;
            this.command = command;
            return ArthasExecuteResponse.builder()
                    .requestNo("REQ-1")
                    .appId(request.getAppId())
                    .env(request.getEnv())
                    .command(command)
                    .success(true)
                    .output("ok")
                    .costMillis(1)
                    .build();
        }
    }

    private static class CapturingRecordMapper implements ArthasCommandRecordMapper {

        private final List<ArthasCommandRecord> records = new ArrayList<>();

        @Override
        public int insert(ArthasCommandRecord record) {
            records.add(record);
            return 1;
        }

        @Override
        public List<ArthasCommandRecord> findByTaskNo(String taskNo) {
            return records.stream()
                    .filter(record -> taskNo.equals(record.getTaskNo()))
                    .toList();
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            return 0;
        }
    }

    private static class CapturingDiagnoseTaskMapper implements DiagnoseTaskMapper {

        private final Map<String, DiagnoseTask> tasks = new HashMap<>();

        void save(DiagnoseTask task) {
            tasks.put(task.getTaskNo(), task);
        }

        @Override
        public int insert(DiagnoseTask task) {
            save(task);
            return 1;
        }

        @Override
        public DiagnoseTask findByTaskNo(String taskNo) {
            return tasks.get(taskNo);
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            tasks.remove(taskNo);
            return 1;
        }

        @Override
        public int updateStatus(String taskNo, String status) {
            tasks.get(taskNo).setStatus(status);
            return 1;
        }

        @Override
        public int markInterruptedIfActive(String taskNo, String reason) {
            tasks.get(taskNo).setStatus(DiagnoseTaskStatus.INTERRUPTED.name());
            return 1;
        }

        @Override
        public java.util.List<DiagnoseTask> findActiveTasks() {
            return java.util.List.of();
        }

        @Override
        public int updateIntent(String taskNo, String diagnoseType, String targetClass, String targetMethod) {
            DiagnoseTask task = tasks.get(taskNo);
            task.setDiagnoseType(diagnoseType);
            task.setTargetClass(targetClass);
            task.setTargetMethod(targetMethod);
            return 1;
        }

        @Override
        public int finishTask(String taskNo, String status, String conclusion, String errorMessage) {
            DiagnoseTask task = tasks.get(taskNo);
            task.setStatus(status);
            task.setConclusion(conclusion);
            task.setErrorMessage(errorMessage);
            return 1;
        }

        @Override
        public java.util.List<com.wuxx.diagnosis.domain.DiagnoseTaskListItem> pageQuery(
                com.wuxx.diagnosis.domain.DiagnoseTaskQuery query, int offset, int limit) {
            return java.util.List.of();
        }

        @Override
        public long count(com.wuxx.diagnosis.domain.DiagnoseTaskQuery query) {
            return 0L;
        }

        @Override
        public java.util.Map<String, Object> countByStatus(java.time.LocalDateTime startTime) {
            return java.util.Map.of();
        }

        @Override
        public java.util.List<java.util.Map<String, Object>> countByType(java.time.LocalDateTime startTime) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<java.util.Map<String, Object>> dailyTrend(java.time.LocalDateTime startTime) {
            return java.util.List.of();
        }
    }
}
