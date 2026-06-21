package com.wuxx.diagnosis.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "diagnosis.kb")
public class KnowledgeBaseProperties {

    private boolean enabled;
    private boolean adminEnabled = true;
    private String adminToken;
    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int retrieveTopK = 5;
    private int candidateMultiplier = 4;
    private double minScore = 0.55;
    private int maxContextLength = 10000;
    private boolean autoIngestHistory = true;
    private int historyMinContentLength = 200;
    private String embeddingModel = "text-embedding-v3";
    private int embeddingDimension = 1024;
    private String vectorStoreFile = "data/kb-simple-vector-store.json";
}
