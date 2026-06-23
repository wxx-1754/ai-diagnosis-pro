package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.wuxx.diagnosis.domain.DiagnoseReport;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.DiagnoseReportMapper;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import org.junit.jupiter.api.Test;

class DiagnoseTaskServiceTest {

    @Test
    void deleteTaskRemovesTaskReportAndCommandRecords() {
        CapturingTaskMapper taskMapper = new CapturingTaskMapper();
        CapturingReportMapper reportMapper = new CapturingReportMapper();
        CapturingCommandRecordMapper commandRecordMapper = new CapturingCommandRecordMapper();
        taskMapper.save(task("DIAG-1", DiagnoseTaskStatus.FINISHED.name()));
        reportMapper.reports.put("DIAG-1", new DiagnoseReport());
        commandRecordMapper.deleted.add("DIAG-1");

        new DiagnoseTaskService(taskMapper, commandRecordMapper, reportMapper).deleteTask("DIAG-1");

        assertThat(taskMapper.tasks).doesNotContainKey("DIAG-1");
        assertThat(reportMapper.reports).doesNotContainKey("DIAG-1");
        assertThat(taskMapper.deleted).containsExactly("DIAG-1");
        assertThat(reportMapper.deleted).containsExactly("DIAG-1");
    }

    @Test
    void deleteTaskRejectsRunningTask() {
        CapturingTaskMapper taskMapper = new CapturingTaskMapper();
        taskMapper.save(task("DIAG-2", DiagnoseTaskStatus.RUNNING.name()));

        DiagnoseTaskService service = new DiagnoseTaskService(taskMapper, new CapturingCommandRecordMapper(), new CapturingReportMapper());

        assertThatThrownBy(() -> service.deleteTask("DIAG-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("进行中");
        assertThat(taskMapper.tasks).containsKey("DIAG-2");
    }

    @Test
    void deleteTaskRejectsCreatedTask() {
        CapturingTaskMapper taskMapper = new CapturingTaskMapper();
        taskMapper.save(task("DIAG-3", DiagnoseTaskStatus.CREATED.name()));

        DiagnoseTaskService service = new DiagnoseTaskService(taskMapper, new CapturingCommandRecordMapper(), new CapturingReportMapper());

        assertThatThrownBy(() -> service.deleteTask("DIAG-3"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(taskMapper.tasks).containsKey("DIAG-3");
    }

    private DiagnoseTask task(String taskNo, String status) {
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo(taskNo);
        task.setAppId("order-service");
        task.setEnv("test");
        task.setStatus(status);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private static class CapturingTaskMapper implements DiagnoseTaskMapper {

        final Map<String, DiagnoseTask> tasks = new HashMap<>();
        final java.util.List<String> deleted = new java.util.ArrayList<>();

        void save(DiagnoseTask task) {
            tasks.put(task.getTaskNo(), task);
        }

        @Override
        public int insert(DiagnoseTask task) {
            tasks.put(task.getTaskNo(), task);
            return 1;
        }

        @Override
        public DiagnoseTask findByTaskNo(String taskNo) {
            return tasks.get(taskNo);
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            tasks.remove(taskNo);
            deleted.add(taskNo);
            return 1;
        }

        @Override
        public int updateStatus(String taskNo, String status) {
            return 0;
        }

        @Override
        public int markInterruptedIfActive(String taskNo, String reason) {
            DiagnoseTask task = tasks.get(taskNo);
            if (task == null || (!DiagnoseTaskStatus.CREATED.name().equals(task.getStatus())
                    && !DiagnoseTaskStatus.RUNNING.name().equals(task.getStatus()))) {
                return 0;
            }
            task.setStatus(DiagnoseTaskStatus.INTERRUPTED.name());
            task.setErrorMessage(reason);
            return 1;
        }

        @Override
        public java.util.List<DiagnoseTask> findActiveTasks() {
            return tasks.values().stream()
                    .filter(task -> DiagnoseTaskStatus.CREATED.name().equals(task.getStatus())
                            || DiagnoseTaskStatus.RUNNING.name().equals(task.getStatus()))
                    .toList();
        }

        @Override
        public int updateIntent(String taskNo, String diagnoseType, String targetClass, String targetMethod) {
            return 0;
        }

        @Override
        public int finishTask(String taskNo, String status, String conclusion, String errorMessage) {
            return 0;
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

    private static class CapturingReportMapper implements DiagnoseReportMapper {

        final Map<String, DiagnoseReport> reports = new HashMap<>();
        final java.util.List<String> deleted = new java.util.ArrayList<>();

        @Override
        public int insert(DiagnoseReport report) {
            return 0;
        }

        @Override
        public int updateByTaskNo(DiagnoseReport report) {
            return 0;
        }

        @Override
        public DiagnoseReport findByTaskNo(String taskNo) {
            return reports.get(taskNo);
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            reports.remove(taskNo);
            deleted.add(taskNo);
            return 1;
        }
    }

    private static class CapturingCommandRecordMapper implements ArthasCommandRecordMapper {

        final java.util.List<String> deleted = new java.util.ArrayList<>();

        @Override
        public int insert(com.wuxx.diagnosis.domain.ArthasCommandRecord record) {
            return 0;
        }

        @Override
        public java.util.List<com.wuxx.diagnosis.domain.ArthasCommandRecord> findByTaskNo(String taskNo) {
            return java.util.List.of();
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            deleted.add(taskNo);
            return 1;
        }

        @Override
        public long countByAppIdAndEnv(String appId, String env) {
            return 0;
        }
    }
}
