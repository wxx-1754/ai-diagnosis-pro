package com.wuxx.diagnosis.service.ai;

import java.util.Collections;
import java.util.List;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
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
public class HybridDiagnosisExecutor {

    public static final String MODE_RULE_FIRST = "RULE_FIRST";

    public static final String MODE_TOOL_CALLING = "TOOL_CALLING";

    private final DiagnoseTaskService diagnoseTaskService;

    private final ToolCallingDiagnosisAgent toolCallingDiagnosisAgent;

    private final RuleBasedDiagnoseExecutor ruleBasedDiagnoseExecutor;

    private final ArthasCommandRecordMapper commandRecordMapper;

    public DiagnoseRunResponse run(String taskNo, String mode) {
        String normalizedMode = normalizeMode(mode);
        if (MODE_RULE_FIRST.equals(normalizedMode)) {
            return fallbackToRule(taskNo, "用户选择 RULE_FIRST 模式");
        }

        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        int recordCountBefore = findRecords(taskNo).size();
        try {
            diagnoseTaskService.markRunning(taskNo);
            log.info("Tool Calling diagnosis started, taskNo={}, diagnoseType={}, targetClass={}, targetMethod={}",
                    taskNo, task.getDiagnoseType(), task.getTargetClass(), task.getTargetMethod());
            String aiResult = toolCallingDiagnosisAgent.diagnose(task);
            List<ArthasCommandRecord> newRecords = findRecords(taskNo).stream()
                    .skip(recordCountBefore)
                    .toList();

            log.info("Tool Calling diagnosis raw result, taskNo={}, newRecordCount={}, aiResultBlank={}",
                    taskNo, newRecords.size(), !StringUtils.hasText(aiResult));
            if (!StringUtils.hasText(aiResult) || newRecords.isEmpty()) {
                return fallbackToRule(taskNo, "AI Tool Calling 未调用任何工具");
            }
            if (newRecords.stream().anyMatch(record -> !Boolean.TRUE.equals(record.getSuccess()))) {
                return fallbackToRule(taskNo, "AI Tool Calling 存在失败工具调用");
            }

            log.info("Tool Calling diagnosis succeeded, taskNo={}, arthasRecordCount={}", taskNo, newRecords.size());
            return DiagnoseRunResponse.builder()
                    .taskNo(taskNo)
                    .status(DiagnoseTaskStatus.FINISHED.name())
                    .conclusion(aiResult)
                    .commandResults(Collections.emptyList())
                    .build();
        } catch (Exception exception) {
            log.warn("Tool Calling diagnosis failed, fallback to rule flow, taskNo={}, message={}",
                    taskNo, exception.getMessage());
            return fallbackToRule(taskNo, "AI Tool Calling 执行异常：" + exception.getMessage());
        }
    }

    private DiagnoseRunResponse fallbackToRule(String taskNo, String reason) {
        log.info("Run rule based fallback, taskNo={}, reason={}", taskNo, reason);
        return ruleBasedDiagnoseExecutor.run(taskNo);
    }

    private List<ArthasCommandRecord> findRecords(String taskNo) {
        List<ArthasCommandRecord> records = commandRecordMapper.findByTaskNo(taskNo);
        return records == null ? Collections.emptyList() : records;
    }

    private String normalizeMode(String mode) {
        if (MODE_RULE_FIRST.equalsIgnoreCase(mode)) {
            return MODE_RULE_FIRST;
        }
        return MODE_TOOL_CALLING;
    }
}
