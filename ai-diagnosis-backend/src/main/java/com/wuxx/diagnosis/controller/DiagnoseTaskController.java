package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.domain.DiagnoseOverviewStats;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskCreateResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskDetailResponse;
import com.wuxx.diagnosis.domain.DiagnoseTaskFinishRequest;
import com.wuxx.diagnosis.domain.DiagnoseTaskListItem;
import com.wuxx.diagnosis.domain.DiagnoseTaskQuery;
import com.wuxx.diagnosis.domain.PageResponse;
import com.wuxx.diagnosis.service.DiagnoseOverviewService;
import com.wuxx.diagnosis.service.DiagnoseTaskQueryService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diagnose/tasks")
public class DiagnoseTaskController {

    private final DiagnoseTaskService diagnoseTaskService;

    private final DiagnoseTaskQueryService diagnoseTaskQueryService;

    private final DiagnoseOverviewService diagnoseOverviewService;

    @PostMapping
    public DiagnoseTaskCreateResponse createTask(@Valid @RequestBody DiagnoseTaskCreateRequest request) {
        log.info("Received diagnose task create request, appId={}, env={}, diagnoseType={}",
                request.getAppId(), request.getEnv(), request.getDiagnoseType());
        return diagnoseTaskService.createTask(request);
    }

    @GetMapping
    public PageResponse<DiagnoseTaskListItem> list(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String diagnoseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        DiagnoseTaskQuery query = new DiagnoseTaskQuery();
        query.setAppId(trim(appId));
        query.setEnv(trim(env));
        query.setDiagnoseType(trim(diagnoseType));
        query.setStatus(trim(status));
        query.setKeyword(trim(keyword));
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return diagnoseOverviewService.page(query);
    }

    @GetMapping("/stats")
    public DiagnoseOverviewStats stats(@RequestParam(defaultValue = "7") Integer days) {
        return diagnoseOverviewService.stats(days == null ? 7 : days);
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

    @DeleteMapping("/{taskNo}")
    public void delete(@PathVariable String taskNo) {
        log.info("Delete diagnose task, taskNo={}", taskNo);
        diagnoseTaskService.deleteTask(taskNo);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
