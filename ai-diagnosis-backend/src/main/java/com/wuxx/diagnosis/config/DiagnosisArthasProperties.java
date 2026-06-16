package com.wuxx.diagnosis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "diagnosis.arthas")
public class DiagnosisArthasProperties {

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 10000;

    private int maxOutputLength = 20000;

    private int auditOutputExcerptLength = 4000;
}
