package com.wuxx.diagnosis.sql.controller;

import com.wuxx.diagnosis.sql.domain.JavaSqlJointDiagnosisRequest;
import com.wuxx.diagnosis.sql.domain.JavaSqlJointDiagnosisResponse;
import com.wuxx.diagnosis.sql.service.JavaSqlJointDiagnosisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
@RequestMapping("/api/joint-diagnose")
public class JavaSqlJointDiagnosisController {

    private final JavaSqlJointDiagnosisService jointDiagnosisService;

    @PostMapping("/java-sql")
    public JavaSqlJointDiagnosisResponse diagnose(@Valid @RequestBody JavaSqlJointDiagnosisRequest request) {
        return jointDiagnosisService.start(request);
    }
}
