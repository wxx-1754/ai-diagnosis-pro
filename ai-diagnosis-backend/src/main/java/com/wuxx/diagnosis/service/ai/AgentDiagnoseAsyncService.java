package com.wuxx.diagnosis.service.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.domain.ai.AgentDiagnoseStartRequest;
import com.wuxx.diagnosis.domain.ai.DiagnoseIntentResult;
import com.wuxx.diagnosis.domain.ai.DiagnosisInsightSummary;
import com.wuxx.diagnosis.domain.ai.DiagnosisReportPayload;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import com.wuxx.diagnosis.knowledge.ingestion.HistoryReportIngestor;
import com.wuxx.diagnosis.knowledge.mapper.DiagnoseReportReferenceMapper;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.service.DiagnoseEventService;
import com.wuxx.diagnosis.service.TaskExecutionRegistry;
import com.wuxx.diagnosis.sql.ai.JavaSqlJointReportGenerator;
import com.wuxx.diagnosis.sql.domain.SqlDiagnosisRecord;
import com.wuxx.diagnosis.sql.mapper.SqlDiagnosisRecordMapper;
import com.wuxx.diagnosis.sql.security.SqlSensitiveDataMasker;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class AgentDiagnoseAsyncService {

    private final DiagnoseTaskService diagnoseTaskService;

    private final DiagnoseIntentClassifier intentClassifier;

    private final HybridDiagnosisExecutor hybridDiagnosisExecutor;

    private final DiagnoseSseManager sseManager;

    private final Executor diagnosisExecutor;

    private final DiagnosisReportGenerator reportGenerator;

    private final DiagnoseReportService diagnoseReportService;

    private final ArthasCommandRecordMapper commandRecordMapper;

    private final DiagnosisAiProperties properties;

    private final DiagnosisInsightSummarizer insightSummarizer;

    private final ObjectMapper objectMapper;

    private final TaskExecutionRegistry executionRegistry;

    private final DiagnoseEventService diagnoseEventService;

    private final SqlDiagnosisRecordMapper sqlDiagnosisRecordMapper;

    private final JavaSqlJointReportGenerator jointReportGenerator;

    private final SqlSensitiveDataMasker sqlSensitiveDataMasker;

    private DiagnoseReportReferenceMapper reportReferenceMapper;

    @Autowired(required = false)
    public void setReportReferenceMapper(DiagnoseReportReferenceMapper reportReferenceMapper) {
        this.reportReferenceMapper = reportReferenceMapper;
    }

    private HistoryReportIngestor historyReportIngestor;

    @Autowired(required = false)
    public void setHistoryReportIngestor(HistoryReportIngestor historyReportIngestor) {
        this.historyReportIngestor = historyReportIngestor;
    }

    public AgentDiagnoseAsyncService(DiagnoseTaskService diagnoseTaskService,
                                     DiagnoseIntentClassifier intentClassifier,
                                     HybridDiagnosisExecutor hybridDiagnosisExecutor,
                                     DiagnoseSseManager sseManager,
                                     @Qualifier("diagnosisExecutor") Executor diagnosisExecutor,
                                     DiagnosisReportGenerator reportGenerator,
                                     DiagnoseReportService diagnoseReportService,
                                     ArthasCommandRecordMapper commandRecordMapper,
                                     DiagnosisAiProperties properties,
                                     DiagnosisInsightSummarizer insightSummarizer,
                                     ObjectMapper objectMapper,
                                     TaskExecutionRegistry executionRegistry,
                                     DiagnoseEventService diagnoseEventService,
                                     SqlDiagnosisRecordMapper sqlDiagnosisRecordMapper,
                                     JavaSqlJointReportGenerator jointReportGenerator,
                                     SqlSensitiveDataMasker sqlSensitiveDataMasker) {
        this.diagnoseTaskService = diagnoseTaskService;
        this.intentClassifier = intentClassifier;
        this.hybridDiagnosisExecutor = hybridDiagnosisExecutor;
        this.sseManager = sseManager;
        this.diagnosisExecutor = diagnosisExecutor;
        this.reportGenerator = reportGenerator;
        this.diagnoseReportService = diagnoseReportService;
        this.commandRecordMapper = commandRecordMapper;
        this.properties = properties;
        this.insightSummarizer = insightSummarizer;
        this.objectMapper = objectMapper;
        this.executionRegistry = executionRegistry;
        this.diagnoseEventService = diagnoseEventService;
        this.sqlDiagnosisRecordMapper = sqlDiagnosisRecordMapper;
        this.jointReportGenerator = jointReportGenerator;
        this.sqlSensitiveDataMasker = sqlSensitiveDataMasker;
    }

    public String start(AgentDiagnoseStartRequest request) {
        DiagnoseTaskCreateResponse createResponse = diagnoseTaskService.createTask(buildCreateRequest(request));
        String taskNo = createResponse.getTaskNo();
        send(taskNo, DiagnoseEventType.TASK_CREATED, "诊断任务已创建", createResponse);
        executionRegistry.register(taskNo);
        try {
            diagnosisExecutor.execute(() -> runAsync(taskNo, request));
        } catch (RuntimeException exception) {
            executionRegistry.unregister(taskNo);
            diagnoseTaskService.markFailed(taskNo, "诊断任务提交失败：" + exception.getMessage());
            send(taskNo, DiagnoseEventType.TASK_FAILED, "诊断任务提交失败：" + exception.getMessage(), null);
            throw exception;
        }
        return taskNo;
    }

    public String restart(String sourceTaskNo) {
        DiagnoseTask source = diagnoseTaskService.getByTaskNo(sourceTaskNo);
        if (!DiagnoseTaskStatus.INTERRUPTED.name().equals(source.getStatus())) {
            throw new IllegalStateException("只有 INTERRUPTED 状态的诊断任务才能重新诊断，taskNo=" + sourceTaskNo);
        }
        AgentDiagnoseStartRequest request = new AgentDiagnoseStartRequest();
        request.setAppId(source.getAppId());
        request.setEnv(source.getEnv());
        request.setUserId(source.getUserId());
        request.setQuestion(source.getQuestion());
        request.setTargetUri(source.getTargetUri());
        request.setTargetClass(source.getTargetClass());
        request.setTargetMethod(source.getTargetMethod());
        request.setMode(diagnoseEventService.findExecutionMode(sourceTaskNo));
        return start(request);
    }

    private void runAsync(String taskNo, AgentDiagnoseStartRequest request) {
        try {
            log.info("Agent diagnose started, taskNo={}, appId={}, env={}, mode={}, question={}",
                    taskNo, request.getAppId(), request.getEnv(), request.getMode(), request.getQuestion());
            send(taskNo, DiagnoseEventType.INTENT_CLASSIFYING, "AI 正在识别诊断类型", null);
            DiagnoseIntentResult intent = classify(request);
            String diagnoseType = normalizeDiagnoseType(intent.getDiagnoseType());
            rejectUnknownOrLowConfidence(intent, diagnoseType);

            String targetClass = chooseFirst(request.getTargetClass(), intent.getTargetClass());
            String targetMethod = chooseFirst(request.getTargetMethod(), intent.getTargetMethod());
            validateTarget(diagnoseType, targetClass, targetMethod);
            diagnoseTaskService.updateIntent(taskNo, diagnoseType, targetClass, targetMethod);

            send(taskNo, DiagnoseEventType.INTENT_CLASSIFIED, "AI 识别诊断类型：" + diagnoseType, intent);
            send(taskNo, DiagnoseEventType.PLAN_CREATED, "正在执行 Agent 诊断流程", request.getMode());

            DiagnoseRunResponse runResponse = hybridDiagnosisExecutor.run(taskNo, request.getMode());
            if (!DiagnoseTaskStatus.FINISHED.name().equals(runResponse.getStatus())) {
                DiagnoseTask failedTask = diagnoseTaskService.getByTaskNo(taskNo);
                throw new IllegalStateException(nullToDefault(
                        failedTask.getErrorMessage(),
                        nullToDefault(runResponse.getConclusion(), "诊断流程执行失败")
                ));
            }

            log.info("Agent diagnose execution finished, taskNo={}, conclusion={}",
                    taskNo, truncate(runResponse.getConclusion(), 500));
            send(taskNo, DiagnoseEventType.AI_ANALYZING, "AI 正在根据 Arthas 输出生成诊断报告", null);
            DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
            List<ArthasCommandRecord> records = commandRecordMapper.findByTaskNo(taskNo);
            String reportMarkdown = generateReport(task, records, runResponse);
            DiagnosisInsightSummary insightSummary = insightSummarizer.summarize(reportMarkdown);
            String summary = insightSummary.getRootCause();
            diagnoseReportService.saveOrUpdate(
                    taskNo,
                    reportMarkdown.startsWith("# Java + SQL") ? "Java + SQL 联合诊断报告" : "Java 应用智能诊断报告",
                    reportMarkdown,
                    serializeInsight(insightSummary),
                    properties.getChatModel(),
                    properties.getPromptVersion()
            );
            attachKnowledgeReferences(taskNo);
            diagnoseTaskService.markFinished(taskNo, summary);

            DiagnosisReportPayload payload = DiagnosisReportPayload.builder()
                    .reportMarkdown(reportMarkdown)
                    .insightSummary(insightSummary)
                    .references(references(taskNo))
                    .build();
            send(taskNo, DiagnoseEventType.REPORT_GENERATED, "AI 诊断报告与摘要已生成", payload);
            send(taskNo, DiagnoseEventType.TASK_FINISHED, "诊断完成", payload);
            log.info("Agent diagnose completed, taskNo={}, rootCause={}", taskNo, truncate(summary, 500));
            ingestHistoryReport(taskNo);
        } catch (Exception exception) {
            log.error("Agent diagnose async failed, taskNo={}", taskNo, exception);
            diagnoseTaskService.markFailed(taskNo, exception.getMessage());
            send(taskNo, DiagnoseEventType.TASK_FAILED, "诊断失败：" + exception.getMessage(), null);
        } finally {
            executionRegistry.unregister(taskNo);
            sseManager.complete(taskNo);
        }
    }

    private DiagnoseTaskCreateRequest buildCreateRequest(AgentDiagnoseStartRequest request) {
        DiagnoseTaskCreateRequest createRequest = new DiagnoseTaskCreateRequest();
        createRequest.setAppId(request.getAppId());
        createRequest.setEnv(request.getEnv());
        createRequest.setUserId(request.getUserId());
        createRequest.setQuestion(request.getQuestion());
        createRequest.setDiagnoseType(DiagnoseType.UNKNOWN.name());
        createRequest.setTargetUri(request.getTargetUri());
        createRequest.setTargetClass(request.getTargetClass());
        createRequest.setTargetMethod(request.getTargetMethod());
        return createRequest;
    }

    private DiagnoseIntentResult classify(AgentDiagnoseStartRequest request) {
        DiagnoseIntentResult intent = intentClassifier.classify(
                request.getQuestion(),
                request.getTargetClass(),
                request.getTargetMethod()
        );
        if (intent != null) {
            return intent;
        }
        DiagnoseIntentResult fallback = new DiagnoseIntentResult();
        fallback.setDiagnoseType(DiagnoseType.UNKNOWN.name());
        fallback.setConfidence(0.0);
        fallback.setReason("AI 返回为空");
        return fallback;
    }

    private String generateReport(DiagnoseTask task,
                                  List<ArthasCommandRecord> records,
                                  DiagnoseRunResponse runResponse) {
        try {
            SqlDiagnosisRecord sqlRecord = sqlDiagnosisRecordMapper.findLatestByTaskNo(task.getTaskNo());
            if (sqlRecord != null && "FINISHED".equals(sqlRecord.getStatus())) {
                log.info("Generating joint Java+SQL report, taskNo={}, sqlRecordId={}, mainTable={}",
                        task.getTaskNo(), sqlRecord.getId(), sqlRecord.getMainTableName());
                send(task.getTaskNo(), DiagnoseEventType.JOINT_REPORT_GENERATING,
                        "正在融合 Java 调用链、SQL Explain 与索引证据", null);
                String jointReport = jointReportGenerator.generate(
                        task,
                        records,
                        null,
                        sqlRecord,
                        sqlSensitiveDataMasker.maskForAi(sqlRecord.getOriginalSql())
                );
                sqlRecord.setDiagnosisResult(jointReport);
                sqlRecord.setUpdatedAt(LocalDateTime.now());
                sqlDiagnosisRecordMapper.updateResult(sqlRecord);
                send(task.getTaskNo(), DiagnoseEventType.JOINT_REPORT_GENERATED,
                        "Java + SQL 联合诊断报告已生成", null);
                return jointReport;
            }
            log.info("Generating Java-only report, taskNo={}, arthasRecordCount={}", task.getTaskNo(), records.size());
            return reportGenerator.generateMarkdownReport(task, records);
        } catch (Exception exception) {
            log.warn("AI report generation failed, save fallback report, taskNo={}, message={}",
                    task.getTaskNo(), exception.getMessage());
            return """
                    # Java 应用智能诊断报告

                    ## 1. 问题现象
                    %s

                    ## 2. 诊断类型
                    %s

                    ## 3. 执行步骤
                    已完成当前诊断类型对应的基础采样流程。

                    ## 4. 关键发现
                    %s

                    ## 5. 根因分析
                    当前 AI 报告生成失败，只能提供基础诊断结论；根因仍需结合命令审计记录进一步确认。

                    ## 6. 预期效果
                    暂无可靠估算，需在修复后通过压测或同口径监控验证。

                    ## 7. 推荐操作
                    1. 查看本任务的 Arthas 命令审计记录，确认关键线程、方法或资源指标。
                    2. 在测试或灰度环境实施低风险修复，并记录变更前基线。
                    3. 使用相同流量和指标口径验证修复效果，未达预期时回滚并补充证据。

                    ## 8. 风险提示
                    AI 报告生成失败，当前内容来自诊断流程基础结论。请结合命令审计记录进一步确认。

                    ## 9. 结论摘要
                    %s
                    """.formatted(
                    nullToDefault(task.getQuestion(), "未提供问题描述"),
                    nullToDefault(task.getDiagnoseType(), DiagnoseType.UNKNOWN.name()),
                    nullToDefault(runResponse.getConclusion(), "诊断数据采集完成，但缺少明确结论。"),
                    nullToDefault(runResponse.getConclusion(), "诊断数据采集完成，但缺少明确结论。")
            );
        }
    }

    private void rejectUnknownOrLowConfidence(DiagnoseIntentResult intent, String diagnoseType) {
        if (DiagnoseType.UNKNOWN.name().equals(diagnoseType)) {
            throw new IllegalArgumentException("无法识别诊断类型，请明确说明是 CPU 高、内存异常、线程阻塞或接口慢。识别原因："
                    + nullToDefault(intent.getReason(), "无"));
        }
        if (intent.getConfidence() != null && intent.getConfidence() < properties.getMinConfidence()) {
            throw new IllegalArgumentException("AI 识别置信度过低，请补充问题现象或手动选择诊断类型。识别原因："
                    + nullToDefault(intent.getReason(), "无"));
        }
    }

    private void validateTarget(String diagnoseType, String targetClass, String targetMethod) {
        if (DiagnoseType.SLOW_REQUEST.name().equals(diagnoseType)
                && (!StringUtils.hasText(targetClass) || !StringUtils.hasText(targetMethod))) {
            throw new IllegalArgumentException("接口慢诊断需要提供 targetClass 和 targetMethod");
        }
    }

    private String normalizeDiagnoseType(String diagnoseType) {
        if (!StringUtils.hasText(diagnoseType)) {
            return DiagnoseType.UNKNOWN.name();
        }
        try {
            return DiagnoseType.valueOf(diagnoseType.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            return DiagnoseType.UNKNOWN.name();
        }
    }

    private String chooseFirst(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String serializeInsight(DiagnosisInsightSummary insightSummary) {
        try {
            return objectMapper.writeValueAsString(insightSummary);
        } catch (JsonProcessingException exception) {
            log.warn("Serialize diagnosis insight failed, message={}", exception.getMessage());
            return null;
        }
    }

    private void send(String taskNo, DiagnoseEventType eventType, String message, Object data) {
        sseManager.send(taskNo, DiagnoseEvent.builder()
                .taskNo(taskNo)
                .eventType(eventType.name())
                .message(message)
                .data(data)
                .time(LocalDateTime.now())
                .build());
    }

    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }

    private List<ReportKnowledgeReference> references(String taskNo) {
        return reportReferenceMapper == null ? List.of() : reportReferenceMapper.findByTaskNo(taskNo);
    }

    /**
     * 报告落库后，将本任务的知识引用回填 report_id。
     *
     * <p>引用记录在报告生成前由 {@link ReportKnowledgeContextService#prepare} 检索时写入，
     * 此时 diagnose_report 尚未保存、report_id 为空。此处报告已 saveOrUpdate，
     * 通过 JOIN diagnose_report 把 report_id 补齐，使引用可按报告维度归属与追溯。
     */
    private void attachKnowledgeReferences(String taskNo) {
        if (reportReferenceMapper == null) {
            return;
        }
        try {
            reportReferenceMapper.attachReportId(taskNo);
        } catch (RuntimeException exception) {
            log.warn("Attach knowledge reference report_id failed, taskNo={}, message={}",
                    taskNo, exception.getMessage());
        }
    }

    /**
     * 异步沉淀历史报告为知识库条目。提交到独立线程执行，不阻塞 SSE 关闭与主流程；
     * KB 未启用时 ingestor 为空，直接跳过。
     */
    private void ingestHistoryReport(String taskNo) {
        if (historyReportIngestor == null) {
            return;
        }
        try {
            diagnosisExecutor.execute(() -> historyReportIngestor.ingest(taskNo));
        } catch (RuntimeException exception) {
            log.warn("History report ingest submit failed, taskNo={}, message={}",
                    taskNo, exception.getMessage());
        }
    }
}
