package com.wuxx.diagnosis.controller;

import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/diagnose/tasks")
public class DiagnoseSseController {

    private final DiagnoseSseManager sseManager;

    @GetMapping("/{taskNo}/stream")
    public SseEmitter stream(@PathVariable String taskNo) {
        return sseManager.subscribe(taskNo);
    }
}
