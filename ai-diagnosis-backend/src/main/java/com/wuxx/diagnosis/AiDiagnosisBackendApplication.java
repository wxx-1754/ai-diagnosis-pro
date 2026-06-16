package com.wuxx.diagnosis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiDiagnosisBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDiagnosisBackendApplication.class, args);
        log.info("AI Diagnosis Backend started");
    }

}
