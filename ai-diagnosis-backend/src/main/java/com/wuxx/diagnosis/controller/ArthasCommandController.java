package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.service.ArthasCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/arthas")
public class ArthasCommandController {

    private final ArthasCommandService arthasCommandService;

    @PostMapping("/execute")
    public ArthasExecuteResponse execute(@Valid @RequestBody ArthasExecuteRequest request) {
        log.info("Received Arthas execute request, appId={}, env={}, commandType={}",
                request.getAppId(), request.getEnv(), request.getCommandType());
        return arthasCommandService.execute(request);
    }

    @GetMapping("/health")
    public ArthasExecuteResponse health(@RequestParam @NotBlank String appId, @RequestParam @NotBlank String env) {
        log.info("Received Arthas health request, appId={}, env={}", appId, env);
        ArthasExecuteRequest request = new ArthasExecuteRequest();
        request.setAppId(appId);
        request.setEnv(env);
        request.setCommandType("jvm");
        return arthasCommandService.execute(request);
    }
}
