package com.wuxx.diagnosis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "diagnosis.ai")
public class DiagnosisAiProperties {

    private boolean enable = false;

    private int maxArthasOutputLength = 30000;

    private int perCommandOutputLimit = 8000;

    private double intentTemperature = 0.0;

    private double reportTemperature = 0.1;

    private double minConfidence = 0.6;

    private String promptVersion = "v1";

    private String chatModel = "gpt-4o-mini";
}
