package com.wuxx.diagnosis.service;

import java.util.ArrayList;
import java.util.List;

import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.domain.DiagnosePlan;
import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.domain.DiagnoseStep;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuleBasedDiagnoseExecutor {

    private final DiagnoseTaskService diagnoseTaskService;

    private final DiagnosePlanBuilder diagnosePlanBuilder;

    private final ArthasCommandService arthasCommandService;

    private final BasicConclusionGenerator conclusionGenerator;

    public DiagnoseRunResponse run(String taskNo) {
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        diagnoseTaskService.markRunning(taskNo);

        List<ArthasExecuteResponse> results = new ArrayList<>();
        try {
            DiagnosePlan plan = diagnosePlanBuilder.build(task);
            for (DiagnoseStep step : plan.getSteps()) {
                ArthasExecuteRequest request = new ArthasExecuteRequest();
                request.setTaskNo(task.getTaskNo());
                request.setAppId(task.getAppId());
                request.setEnv(task.getEnv());
                request.setCommandType(step.getCommandType());

                ArthasExecuteResponse response = arthasCommandService.executeCommand(request, step.getCommand());
                results.add(response);

                if (!response.isSuccess()) {
                    throw new IllegalStateException("诊断步骤执行失败：" + step.getPurpose()
                            + "，错误：" + response.getErrorMessage());
                }
            }

            String conclusion = conclusionGenerator.generate(task, results);
            diagnoseTaskService.markFinished(taskNo, conclusion);
            return response(taskNo, DiagnoseTaskStatus.FINISHED.name(), conclusion, results);
        } catch (Exception exception) {
            diagnoseTaskService.markFailed(taskNo, exception.getMessage());
            return response(taskNo, DiagnoseTaskStatus.FAILED.name(), null, results);
        }
    }

    private DiagnoseRunResponse response(String taskNo,
                                         String status,
                                         String conclusion,
                                         List<ArthasExecuteResponse> results) {
        return DiagnoseRunResponse.builder()
                .taskNo(taskNo)
                .status(status)
                .conclusion(conclusion)
                .commandResults(results)
                .build();
    }
}
