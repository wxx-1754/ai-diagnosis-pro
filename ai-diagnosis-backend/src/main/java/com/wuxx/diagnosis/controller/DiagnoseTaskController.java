package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskDetailResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskFinishRequest;
import com.wuxx.diagnosis.service.DiagnoseTaskQueryService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diagnose/tasks")
public class DiagnoseTaskController {

    private final DiagnoseTaskService diagnoseTaskService;

    private final DiagnoseTaskQueryService diagnoseTaskQueryService;

    @PostMapping
    public DiagnoseTaskCreateResponse createTask(@Valid @RequestBody DiagnoseTaskCreateRequest request) {
        log.info("Received diagnose task create request, appId={}, env={}, diagnoseType={}",
                request.getAppId(), request.getEnv(), request.getDiagnoseType());
        return diagnoseTaskService.createTask(request);
    }

    @GetMapping("/{taskNo}")
    public DiagnoseTask getTask(@PathVariable String taskNo) {
        return diagnoseTaskService.getByTaskNo(taskNo);
    }

    @GetMapping("/{taskNo}/detail")
    public DiagnoseTaskDetailResponse detail(@PathVariable String taskNo) {
        return diagnoseTaskQueryService.detail(taskNo);
    }

    @PostMapping("/{taskNo}/finish")
    public void finish(@PathVariable String taskNo, @RequestBody DiagnoseTaskFinishRequest request) {
        diagnoseTaskService.markFinished(taskNo, request.getConclusion());
    }
}
