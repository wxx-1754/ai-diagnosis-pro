package com.wuxx.diagnosis.service.ai;

import java.util.List;

import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.domain.ai.AiDiagnoseRequest;
import com.wuxx.diagnosis.domain.ai.AiDiagnoseResponse;
import com.wuxx.diagnosis.domain.ai.DiagnoseIntentResult;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.service.RuleBasedDiagnoseExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class AiDiagnosisOrchestrator {

    private final DiagnoseIntentClassifier intentClassifier;

    private final DiagnoseTaskService diagnoseTaskService;

    private final RuleBasedDiagnoseExecutor ruleBasedDiagnoseExecutor;

    private final ArthasCommandRecordMapper arthasCommandRecordMapper;

    private final DiagnosisReportGenerator reportGenerator;

    private final DiagnoseReportService diagnoseReportService;

    private final DiagnosisAiProperties properties;

    public AiDiagnoseResponse diagnose(AiDiagnoseRequest request) {
        DiagnoseIntentResult intent = intentClassifier.classify(
                request.getQuestion(),
                request.getTargetClass(),
                request.getTargetMethod()
        );
        if (intent == null) {
            intent = new DiagnoseIntentResult();
            intent.setDiagnoseType(DiagnoseType.UNKNOWN.name());
            intent.setConfidence(0.0);
            intent.setReason("AI 返回为空");
        }

        String diagnoseType = normalizeDiagnoseType(intent.getDiagnoseType());
        rejectUnknownOrLowConfidence(intent, diagnoseType);

        String targetClass = chooseFirst(request.getTargetClass(), intent.getTargetClass());
        String targetMethod = chooseFirst(request.getTargetMethod(), intent.getTargetMethod());
        validateTarget(diagnoseType, targetClass, targetMethod);

        DiagnoseTaskCreateResponse createResponse = diagnoseTaskService.createTask(
                buildCreateRequest(request, diagnoseType, targetClass, targetMethod)
        );

        DiagnoseRunResponse runResponse = ruleBasedDiagnoseExecutor.run(createResponse.getTaskNo());
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(createResponse.getTaskNo());
        if (!DiagnoseTaskStatus.FINISHED.name().equals(runResponse.getStatus())) {
            return AiDiagnoseResponse.builder()
                    .taskNo(task.getTaskNo())
                    .diagnoseType(diagnoseType)
                    .status(runResponse.getStatus())
                    .conclusion(task.getErrorMessage())
                    .build();
        }

        try {
            List<ArthasCommandRecord> records = arthasCommandRecordMapper.findByTaskNo(task.getTaskNo());
            String reportMarkdown = reportGenerator.generateMarkdownReport(task, records);
            String summary = extractSummary(reportMarkdown);
            diagnoseReportService.saveOrUpdate(
                    task.getTaskNo(),
                    "Java 应用智能诊断报告",
                    reportMarkdown,
                    null,
                    properties.getChatModel(),
                    properties.getPromptVersion()
            );
            diagnoseTaskService.markFinished(task.getTaskNo(), summary);

            return AiDiagnoseResponse.builder()
                    .taskNo(task.getTaskNo())
                    .diagnoseType(diagnoseType)
                    .status(DiagnoseTaskStatus.FINISHED.name())
                    .reportMarkdown(reportMarkdown)
                    .conclusion(summary)
                    .build();
        } catch (Exception exception) {
            log.error("AI report generation failed, taskNo={}", task.getTaskNo(), exception);
            return AiDiagnoseResponse.builder()
                    .taskNo(task.getTaskNo())
                    .diagnoseType(diagnoseType)
                    .status(DiagnoseTaskStatus.FINISHED.name())
                    .conclusion(runResponse.getConclusion())
                    .build();
        }
    }

    public AiDiagnoseResponse regenerateReport(String taskNo) {
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        if (!DiagnoseTaskStatus.FINISHED.name().equals(task.getStatus())) {
            throw new IllegalArgumentException("只有 FINISHED 状态的诊断任务才能重新生成报告，taskNo=" + taskNo);
        }

        List<ArthasCommandRecord> records = arthasCommandRecordMapper.findByTaskNo(taskNo);
        String reportMarkdown = reportGenerator.generateMarkdownReport(task, records);
        String summary = extractSummary(reportMarkdown);
        diagnoseReportService.saveOrUpdate(
                task.getTaskNo(),
                "Java 应用智能诊断报告",
                reportMarkdown,
                null,
                properties.getChatModel(),
                properties.getPromptVersion()
        );
        diagnoseTaskService.markFinished(task.getTaskNo(), summary);

        return AiDiagnoseResponse.builder()
                .taskNo(task.getTaskNo())
                .diagnoseType(task.getDiagnoseType())
                .status(DiagnoseTaskStatus.FINISHED.name())
                .reportMarkdown(reportMarkdown)
                .conclusion(summary)
                .build();
    }

    private DiagnoseTaskCreateRequest buildCreateRequest(AiDiagnoseRequest request,
                                                         String diagnoseType,
                                                         String targetClass,
                                                         String targetMethod) {
        DiagnoseTaskCreateRequest createRequest = new DiagnoseTaskCreateRequest();
        createRequest.setAppId(request.getAppId());
        createRequest.setEnv(request.getEnv());
        createRequest.setUserId(request.getUserId());
        createRequest.setQuestion(request.getQuestion());
        createRequest.setDiagnoseType(diagnoseType);
        createRequest.setTargetUri(request.getTargetUri());
        createRequest.setTargetClass(targetClass);
        createRequest.setTargetMethod(targetMethod);
        return createRequest;
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

    private String extractSummary(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "AI 已生成诊断报告。";
        }

        int index = markdown.indexOf("## 9. 结论摘要");
        String summary = index >= 0 ? markdown.substring(index) : markdown;
        return summary.length() > 1000 ? summary.substring(0, 1000) : summary;
    }

    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
