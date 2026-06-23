package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseReport;
import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.domain.ai.AiDiagnoseRequest;
import com.wuxx.diagnosis.domain.ai.AiDiagnoseResponse;
import com.wuxx.diagnosis.domain.ai.DiagnoseIntentResult;
import com.wuxx.diagnosis.domain.ai.DiagnosisInsightSummary;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.mapper.DiagnoseReportMapper;
import com.wuxx.diagnosis.mapper.DiagnoseTaskMapper;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.service.RuleBasedDiagnoseExecutor;
import org.junit.jupiter.api.Test;

class AiDiagnosisOrchestratorTest {

    @Test
    void diagnoseRunsRuleBasedFlowAndStoresReport() {
        DiagnosisAiProperties properties = enabledProperties();
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        CapturingRuleExecutor executor = new CapturingRuleExecutor(DiagnoseTaskStatus.FINISHED.name(), "基础结论");
        CapturingRecordMapper recordMapper = new CapturingRecordMapper();
        CapturingReportMapper reportMapper = new CapturingReportMapper();
        FakeIntentClassifier classifier = new FakeIntentClassifier(properties, intent(DiagnoseType.HIGH_CPU.name(), 0.95));
        FakeReportGenerator reportGenerator = new FakeReportGenerator(properties, """
                # Java 应用智能诊断报告

                ## 9. 结论摘要
                CPU 高诊断证据充分。
                """);
        AiDiagnosisOrchestrator orchestrator = orchestrator(
                properties,
                classifier,
                taskMapper,
                executor,
                recordMapper,
                reportMapper,
                reportGenerator
        );

        AiDiagnoseResponse response = orchestrator.diagnose(request("order-service CPU 很高"));

        assertThat(response.getStatus()).isEqualTo(DiagnoseTaskStatus.FINISHED.name());
        assertThat(response.getDiagnoseType()).isEqualTo(DiagnoseType.HIGH_CPU.name());
        assertThat(response.getReportMarkdown()).contains("Java 应用智能诊断报告");
        assertThat(response.getConclusion()).isEqualTo("热点线程导致 CPU 持续升高。");
        assertThat(response.getInsightSummary().getSpecificReasons()).hasSize(2);
        assertThat(response.getInsightSummary().getRecommendedActions()).hasSize(2);
        assertThat(executor.runTaskNos).containsExactly(response.getTaskNo());
        assertThat(taskMapper.tasks.get(response.getTaskNo()).getDiagnoseType()).isEqualTo(DiagnoseType.HIGH_CPU.name());
        assertThat(taskMapper.tasks.get(response.getTaskNo()).getConclusion()).isEqualTo("热点线程导致 CPU 持续升高。");
        assertThat(reportMapper.reports.get(response.getTaskNo()).getReportMarkdown()).contains("结论摘要");
        assertThat(reportMapper.reports.get(response.getTaskNo()).getReportJson()).contains("rootCause");
    }

    @Test
    void slowRequestWithoutTargetDoesNotCreateTaskOrRunTrace() {
        DiagnosisAiProperties properties = enabledProperties();
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        CapturingRuleExecutor executor = new CapturingRuleExecutor(DiagnoseTaskStatus.FINISHED.name(), "基础结论");
        AiDiagnosisOrchestrator orchestrator = orchestrator(
                properties,
                new FakeIntentClassifier(properties, intent(DiagnoseType.SLOW_REQUEST.name(), 0.95)),
                taskMapper,
                executor,
                new CapturingRecordMapper(),
                new CapturingReportMapper(),
                new FakeReportGenerator(properties, "report")
        );

        assertThatThrownBy(() -> orchestrator.diagnose(request("下单接口很慢")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("接口慢诊断需要提供 targetClass 和 targetMethod");
        assertThat(taskMapper.tasks).isEmpty();
        assertThat(executor.runTaskNos).isEmpty();
    }

    @Test
    void lowConfidenceIntentDoesNotCreateTask() {
        DiagnosisAiProperties properties = enabledProperties();
        CapturingDiagnoseTaskMapper taskMapper = new CapturingDiagnoseTaskMapper();
        CapturingRuleExecutor executor = new CapturingRuleExecutor(DiagnoseTaskStatus.FINISHED.name(), "基础结论");
        AiDiagnosisOrchestrator orchestrator = orchestrator(
                properties,
                new FakeIntentClassifier(properties, intent(DiagnoseType.HIGH_CPU.name(), 0.2)),
                taskMapper,
                executor,
                new CapturingRecordMapper(),
                new CapturingReportMapper(),
                new FakeReportGenerator(properties, "report")
        );

        assertThatThrownBy(() -> orchestrator.diagnose(request("可能有点问题")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI 识别置信度过低");
        assertThat(taskMapper.tasks).isEmpty();
        assertThat(executor.runTaskNos).isEmpty();
    }

    private AiDiagnosisOrchestrator orchestrator(DiagnosisAiProperties properties,
                                                 DiagnoseIntentClassifier classifier,
                                                 CapturingDiagnoseTaskMapper taskMapper,
                                                 RuleBasedDiagnoseExecutor executor,
                                                 ArthasCommandRecordMapper recordMapper,
                                                 DiagnoseReportMapper reportMapper,
                                                 DiagnosisReportGenerator reportGenerator) {
        DiagnoseTaskService taskService = new DiagnoseTaskService(taskMapper, recordMapper, reportMapper);
        return new AiDiagnosisOrchestrator(
                classifier,
                taskService,
                executor,
                recordMapper,
                reportGenerator,
                new DiagnoseReportService(reportMapper),
                fakeSummarizer(properties),
                new ObjectMapper(),
                properties
        );
    }

    private DiagnosisInsightSummarizer fakeSummarizer(DiagnosisAiProperties properties) {
        return new DiagnosisInsightSummarizer(null, new ObjectMapper(), properties) {
            @Override
            public DiagnosisInsightSummary summarize(String reportMarkdown) {
                DiagnosisInsightSummary summary = new DiagnosisInsightSummary();
                summary.setRootCause("热点线程导致 CPU 持续升高。");
                summary.setSpecificReasons(List.of("热点线程 CPU 占用集中。", "业务方法耗时显著偏高。"));
                summary.setExpectedEffect("预计 CPU 与 P95 下降，需压测验证。");
                summary.setRecommendedActions(List.of("优化热点方法。", "灰度验证指标。"));
                return summary;
            }
        };
    }

    private AiDiagnoseRequest request(String question) {
        AiDiagnoseRequest request = new AiDiagnoseRequest();
        request.setAppId("order-service");
        request.setEnv("test");
        request.setUserId("admin");
        request.setQuestion(question);
        return request;
    }

    private static DiagnoseIntentResult intent(String diagnoseType, double confidence) {
        DiagnoseIntentResult result = new DiagnoseIntentResult();
        result.setDiagnoseType(diagnoseType);
        result.setConfidence(confidence);
        result.setReason("test");
        return result;
    }

    private static DiagnosisAiProperties enabledProperties() {
        DiagnosisAiProperties properties = new DiagnosisAiProperties();
        properties.setEnable(true);
        properties.setMinConfidence(0.6);
        properties.setChatModel("test-model");
        return properties;
    }

    private static class FakeIntentClassifier extends DiagnoseIntentClassifier {

        private final DiagnoseIntentResult result;

        FakeIntentClassifier(DiagnosisAiProperties properties, DiagnoseIntentResult result) {
            super(null, new ObjectMapper(), properties);
            this.result = result;
        }

        @Override
        public DiagnoseIntentResult classify(String question, String targetClass, String targetMethod) {
            return result;
        }
    }

    private static class FakeReportGenerator extends DiagnosisReportGenerator {

        private final String report;

        FakeReportGenerator(DiagnosisAiProperties properties, String report) {
            super(null, properties);
            this.report = report;
        }

        @Override
        public String generateMarkdownReport(DiagnoseTask task, List<ArthasCommandRecord> records) {
            return report;
        }
    }

    private static class CapturingRuleExecutor extends RuleBasedDiagnoseExecutor {

        private final String status;

        private final String conclusion;

        private final List<String> runTaskNos = new ArrayList<>();

        CapturingRuleExecutor(String status, String conclusion) {
            super(null, null, null, null);
            this.status = status;
            this.conclusion = conclusion;
        }

        @Override
        public DiagnoseRunResponse run(String taskNo) {
            runTaskNos.add(taskNo);
            return DiagnoseRunResponse.builder()
                    .taskNo(taskNo)
                    .status(status)
                    .conclusion(conclusion)
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

        @Override
        public long countByAppIdAndEnv(String appId, String env) {
            return 0;
        }
    }

    private static class CapturingReportMapper implements DiagnoseReportMapper {

        private final Map<String, DiagnoseReport> reports = new HashMap<>();

        @Override
        public int insert(DiagnoseReport report) {
            reports.put(report.getTaskNo(), report);
            return 1;
        }

        @Override
        public int updateByTaskNo(DiagnoseReport report) {
            reports.put(report.getTaskNo(), report);
            return 1;
        }

        @Override
        public DiagnoseReport findByTaskNo(String taskNo) {
            return reports.get(taskNo);
        }

        @Override
        public int deleteByTaskNo(String taskNo) {
            reports.remove(taskNo);
            return 1;
        }
    }

    private static class CapturingDiagnoseTaskMapper implements DiagnoseTaskMapper {

        private final Map<String, DiagnoseTask> tasks = new HashMap<>();

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
            return 1;
        }

        @Override
        public int updateStatus(String taskNo, String status) {
            tasks.get(taskNo).setStatus(status);
            tasks.get(taskNo).setUpdatedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int markInterruptedIfActive(String taskNo, String reason) {
            DiagnoseTask task = tasks.get(taskNo);
            task.setStatus(DiagnoseTaskStatus.INTERRUPTED.name());
            task.setErrorMessage(reason);
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
            task.setUpdatedAt(LocalDateTime.now());
            return 1;
        }

        @Override
        public int finishTask(String taskNo, String status, String conclusion, String errorMessage) {
            DiagnoseTask task = tasks.get(taskNo);
            task.setStatus(status);
            task.setConclusion(conclusion);
            task.setErrorMessage(errorMessage);
            task.setUpdatedAt(LocalDateTime.now());
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
