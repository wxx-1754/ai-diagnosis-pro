package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.ai.AgentDiagnoseStartRequest;
import com.wuxx.diagnosis.domain.ai.AgentDiagnoseStartResponse;
import com.wuxx.diagnosis.service.ai.AgentDiagnoseAsyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
@RequestMapping("/api/agent/diagnose")
public class AgentDiagnoseController {

    private final AgentDiagnoseAsyncService agentDiagnoseAsyncService;

    @PostMapping("/start")
    public AgentDiagnoseStartResponse start(@Valid @RequestBody AgentDiagnoseStartRequest request) {
        log.info("Received agent diagnose start request, appId={}, env={}, mode={}",
                request.getAppId(), request.getEnv(), request.getMode());
        String taskNo = agentDiagnoseAsyncService.start(request);
        return AgentDiagnoseStartResponse.builder()
                .taskNo(taskNo)
                .status(DiagnoseTaskStatus.CREATED.name())
                .streamUrl("/api/diagnose/tasks/" + taskNo + "/stream")
                .build();
    }
}
