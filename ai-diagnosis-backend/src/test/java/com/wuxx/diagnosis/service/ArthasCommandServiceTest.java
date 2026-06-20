package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.arthas.ArthasCommandExecutor;
import com.wuxx.diagnosis.arthas.ArthasCommandFactory;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import org.junit.jupiter.api.Test;

class ArthasCommandServiceTest {

    @Test
    void executeBuildsFixedCommandAndSavesAuditRecord() {
        CapturingExecutor executor = new CapturingExecutor(true);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        ArthasCommandService service = service(executor, recordMapper);

        ArthasExecuteResponse response = service.execute(request("topThread"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(executor.command).isEqualTo("thread -n 5");
        assertThat(recordMapper.records).hasSize(1);
        ArthasCommandRecord record = recordMapper.records.getFirst();
        assertThat(record.getRequestNo()).startsWith("ARTHAS-");
        assertThat(record.getAppId()).isEqualTo("order-service");
        assertThat(record.getEnv()).isEqualTo("test");
        assertThat(record.getCommand()).isEqualTo("thread -n 5");
        assertThat(record.getCommandType()).isEqualTo("topThread");
        assertThat(record.getSuccess()).isTrue();
        assertThat(record.getOutputExcerpt()).isEqualTo("12345678");
    }

    @Test
    void executeSavesAuditRecordWhenCommandFails() {
        CapturingExecutor executor = new CapturingExecutor(false);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        ArthasCommandService service = service(executor, recordMapper);

        ArthasExecuteResponse response = service.execute(request("jvm"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(recordMapper.records).hasSize(1);
        ArthasCommandRecord record = recordMapper.records.getFirst();
        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getErrorMessage()).isEqualTo("connecti");
    }

    @Test
    void executeWithTaskNoMarksTaskRunningAndSavesTaskNo() {
        CapturingExecutor executor = new CapturingExecutor(true);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        taskMapper.save(existingTask("DIAG-1"));
        ArthasCommandService service = service(executor, recordMapper, taskMapper);

        ArthasExecuteRequest request = request("dashboard");
        request.setTaskNo("DIAG-1");
        ArthasExecuteResponse response = service.execute(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(taskMapper.statuses.get("DIAG-1")).isEqualTo(DiagnoseTaskStatus.RUNNING.name());
        assertThat(recordMapper.records).hasSize(1);
        assertThat(recordMapper.records.getFirst().getTaskNo()).isEqualTo("DIAG-1");
    }

    @Test
    void executeWithMissingTaskNoFailsBeforeDispatch() {
        CapturingExecutor executor = new CapturingExecutor(true);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        ArthasCommandService service = service(executor, recordMapper, new CapturingDiagnoseTaskMapper());

        ArthasExecuteRequest request = request("jvm");
        request.setTaskNo("DIAG-NOT-FOUND");

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("诊断任务不存在，taskNo=DIAG-NOT-FOUND");
        assertThat(executor.command).isNull();
        assertThat(recordMapper.records).isEmpty();
    }

    @Test
    void executeWithTaskNoMarksTaskFailedWhenCommandFails() {
        CapturingExecutor executor = new CapturingExecutor(false);
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        taskMapper.save(existingTask("DIAG-2"));
        ArthasCommandService service = service(executor, recordMapper, taskMapper);

        ArthasExecuteRequest request = request("jvm");
        request.setTaskNo("DIAG-2");
        ArthasExecuteResponse response = service.execute(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(taskMapper.statuses.get("DIAG-2")).isEqualTo(DiagnoseTaskStatus.FAILED.name());
        assertThat(taskMapper.errorMessages.get("DIAG-2")).isEqualTo("connection refused");
    }

    private ArthasCommandService service(ArthasCommandExecutor executor, ArthasCommandRecordMapper recordMapper) {
        return service(executor, recordMapper, new CapturingDiagnoseTaskMapper());
    }

    private ArthasCommandService service(ArthasCommandExecutor executor,
                                         ArthasCommandRecordMapper recordMapper,
                                         DiagnoseTaskMapper diagnoseTaskMapper) {
        AppInstanceService appInstanceService = new AppInstanceService((appId, env) -> instance());
        DiagnosisArthasProperties properties = new DiagnosisArthasProperties();
        properties.setAuditOutputExcerptLength(8);
        return new ArthasCommandService(
                appInstanceService,
                new ArthasCommandFactory(),
                executor,
                recordMapper,
                properties,
                new DiagnoseTaskService(diagnoseTaskMapper, null, null),
                new DiagnoseSseManager()
        );
    }

    private ArthasExecuteRequest request(String commandType) {
        ArthasExecuteRequest request = new ArthasExecuteRequest();
        request.setAppId("order-service");
        request.setEnv("test");
        request.setCommandType(commandType);
        return request;
    }

    private DiagnoseTask existingTask(String taskNo) {
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo(taskNo);
        task.setAppId("order-service");
        task.setEnv("test");
        task.setDiagnoseType("HIGH_CPU");
        task.setStatus(DiagnoseTaskStatus.CREATED.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private AppInstance instance() {
        AppInstance instance = new AppInstance();
        instance.setAppId("order-service");
        instance.setEnv("test");
        instance.setIp("127.0.0.1");
        instance.setArthasHttpPort(8563);
        instance.setAccessMode("HTTP");
        return instance;
    }

    private static class CapturingExecutor implements ArthasCommandExecutor {

        private final boolean success;

        private String command;

        CapturingExecutor(boolean success) {
            this.success = success;
        }

        @Override
        public ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType) {
            this.command = command;
            return ArthasExecuteResponse.builder()
                    .requestNo(requestNo)
                    .appId(instance.getAppId())
                    .env(instance.getEnv())
                    .command(command)
                    .success(success)
                    .output(success ? "1234567890" : null)
                    .errorMessage(success ? null : "connection refused")
                    .costMillis(10)
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
