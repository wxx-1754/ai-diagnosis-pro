package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.arthas.ArthasCommandFactory;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import org.junit.jupiter.api.Test;

class RuleBasedDiagnoseExecutorTest {

    @Test
    void runHighCpuPlanExecutesAllStepsAndMarksFinished() {
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        taskMapper.save(task("DIAG-1", "HIGH_CPU"));
        CapturingArthasCommandService commandService = new CapturingArthasCommandService(true);
        RuleBasedDiagnoseExecutor executor = executor(taskMapper, commandService);

        DiagnoseRunResponse response = executor.run("DIAG-1");

        assertThat(response.getStatus()).isEqualTo(DiagnoseTaskStatus.FINISHED.name());
        assertThat(response.getConclusion()).contains("CPU 高诊断基础数据采集");
        assertThat(commandService.commands).containsExactly("dashboard -n 1", "thread -n 5");
        assertThat(commandService.commandTypes).containsExactly("dashboard", "topThread");
        assertThat(taskMapper.statuses.get("DIAG-1")).isEqualTo(DiagnoseTaskStatus.FINISHED.name());
        assertThat(taskMapper.conclusions.get("DIAG-1")).contains("thread -n 5");
    }

    @Test
    void runSlowRequestWithoutTargetClassMarksFailedBeforeDispatch() {
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        DiagnoseTask task = task("DIAG-2", "SLOW_REQUEST");
        task.setTargetMethod("createOrder");
        taskMapper.save(task);
        CapturingArthasCommandService commandService = new CapturingArthasCommandService(true);
        RuleBasedDiagnoseExecutor executor = executor(taskMapper, commandService);

        DiagnoseRunResponse response = executor.run("DIAG-2");

        assertThat(response.getStatus()).isEqualTo(DiagnoseTaskStatus.FAILED.name());
        assertThat(commandService.commands).isEmpty();
        assertThat(taskMapper.statuses.get("DIAG-2")).isEqualTo(DiagnoseTaskStatus.FAILED.name());
        assertThat(taskMapper.errorMessages.get("DIAG-2")).isEqualTo("targetClass不能为空");
    }

    private RuleBasedDiagnoseExecutor executor(CapturingDiagnoseTaskMapper taskMapper,
                                               CapturingArthasCommandService commandService) {
        DiagnoseTaskService taskService = new DiagnoseTaskService(taskMapper, null, null);
        return new RuleBasedDiagnoseExecutor(
                taskService,
                new DiagnosePlanBuilder(new ArthasCommandFactory()),
                commandService,
                new BasicConclusionGenerator()
        );
    }

    private DiagnoseTask task(String taskNo, String diagnoseType) {
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo(taskNo);
        task.setAppId("order-service");
        task.setEnv("test");
        task.setDiagnoseType(diagnoseType);
        task.setStatus(DiagnoseTaskStatus.CREATED.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private static class CapturingArthasCommandService extends ArthasCommandService {

        private final boolean success;

        private final List<String> commands = new ArrayList<>();

        private final List<String> commandTypes = new ArrayList<>();

        CapturingArthasCommandService(boolean success) {
            super(null, null, null, null, null, null, null);
            this.success = success;
        }

        @Override
        public ArthasExecuteResponse executeCommand(ArthasExecuteRequest request, String command) {
            commands.add(command);
            commandTypes.add(request.getCommandType());
            return ArthasExecuteResponse.builder()
                    .requestNo("REQ-" + commands.size())
                    .appId(request.getAppId())
                    .env(request.getEnv())
                    .command(command)
                    .success(success)
                    .output(success ? "ok" : null)
                    .errorMessage(success ? null : "connection refused")
                    .costMillis(10)
                    .build();
        }
    }

    private static class CapturingDiagnoseTaskMapper implements DiagnoseTaskMapper {

        private final Map<String, DiagnoseTask> tasks = new HashMap<>();

        private final Map<String, String> statuses = new HashMap<>();

        private final Map<String, String> conclusions = new HashMap<>();

        private final Map<String, String> errorMessages = new HashMap<>();

        void save(DiagnoseTask task) {
            tasks.put(task.getTaskNo(), task);
            statuses.put(task.getTaskNo(), task.getStatus());
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
            statuses.put(taskNo, status);
            return 1;
        }

        @Override
        public int markInterruptedIfActive(String taskNo, String reason) {
            statuses.put(taskNo, DiagnoseTaskStatus.INTERRUPTED.name());
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
            statuses.put(taskNo, status);
            conclusions.put(taskNo, conclusion);
            errorMessages.put(taskNo, errorMessage);
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
