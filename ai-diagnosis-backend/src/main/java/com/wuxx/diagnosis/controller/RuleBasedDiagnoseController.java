package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.domain.DiagnoseRunResponse;
import com.wuxx.diagnosis.service.RuleBasedDiagnoseExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diagnose/tasks")
public class RuleBasedDiagnoseController {

    private final RuleBasedDiagnoseExecutor ruleBasedDiagnoseExecutor;

    @PostMapping("/{taskNo}/run")
    public DiagnoseRunResponse run(@PathVariable String taskNo) {
        log.info("Received rule based diagnose run request, taskNo={}", taskNo);
        return ruleBasedDiagnoseExecutor.run(taskNo);
    }
}
