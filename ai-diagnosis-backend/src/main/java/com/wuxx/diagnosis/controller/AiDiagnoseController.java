package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.domain.DiagnoseReport;
import com.wuxx.diagnosis.domain.ai.AiDiagnoseRequest;
import com.wuxx.diagnosis.domain.ai.AiDiagnoseResponse;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.ai.AiDiagnosisOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
@RequestMapping("/api/ai/diagnose")
public class AiDiagnoseController {

    private final AiDiagnosisOrchestrator aiDiagnosisOrchestrator;

    private final DiagnoseReportService diagnoseReportService;

    @PostMapping
    public AiDiagnoseResponse diagnose(@Valid @RequestBody AiDiagnoseRequest request) {
        log.info("Received AI diagnose request, appId={}, env={}", request.getAppId(), request.getEnv());
        return aiDiagnosisOrchestrator.diagnose(request);
    }

    @GetMapping("/{taskNo}/report")
    public DiagnoseReport getReport(@PathVariable String taskNo) {
        return diagnoseReportService.getByTaskNo(taskNo);
    }

    @PostMapping("/{taskNo}/report/regenerate")
    public AiDiagnoseResponse regenerateReport(@PathVariable String taskNo) {
        log.info("Received AI report regenerate request, taskNo={}", taskNo);
        return aiDiagnosisOrchestrator.regenerateReport(taskNo);
    }
}
