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
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
                                     ObjectMapper objectMapper) {
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
    }

    public String start(AgentDiagnoseStartRequest request) {
        DiagnoseTaskCreateResponse createResponse = diagnoseTaskService.createTask(buildCreateRequest(request));
        String taskNo = createResponse.getTaskNo();
        send(taskNo, DiagnoseEventType.TASK_CREATED, "诊断任务已创建", createResponse);
        diagnosisExecutor.execute(() -> runAsync(taskNo, request));
        return taskNo;
    }

    private void runAsync(String taskNo, AgentDiagnoseStartRequest request) {
        try {
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

            send(taskNo, DiagnoseEventType.AI_ANALYZING, "AI 正在根据 Arthas 输出生成诊断报告", null);
            DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
            List<ArthasCommandRecord> records = commandRecordMapper.findByTaskNo(taskNo);
            String reportMarkdown = generateReport(task, records, runResponse);
            DiagnosisInsightSummary insightSummary = insightSummarizer.summarize(reportMarkdown);
            String summary = insightSummary.getRootCause();
            diagnoseReportService.saveOrUpdate(
                    taskNo,
                    "Java 应用智能诊断报告",
                    reportMarkdown,
                    serializeInsight(insightSummary),
                    properties.getChatModel(),
                    properties.getPromptVersion()
            );
            diagnoseTaskService.markFinished(taskNo, summary);

            DiagnosisReportPayload payload = DiagnosisReportPayload.builder()
                    .reportMarkdown(reportMarkdown)
                    .insightSummary(insightSummary)
                    .build();
            send(taskNo, DiagnoseEventType.REPORT_GENERATED, "AI 诊断报告与摘要已生成", payload);
            send(taskNo, DiagnoseEventType.TASK_FINISHED, "诊断完成", payload);
        } catch (Exception exception) {
            log.error("Agent diagnose async failed, taskNo={}", taskNo, exception);
            diagnoseTaskService.markFailed(taskNo, exception.getMessage());
            send(taskNo, DiagnoseEventType.TASK_FAILED, "诊断失败：" + exception.getMessage(), null);
        } finally {
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
}
