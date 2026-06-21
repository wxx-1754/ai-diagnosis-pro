package com.wuxx.diagnosis.knowledge.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievedChunk {
    private Long chunkId;
    private String docNo;
    private String title;
    private String sourceType;
    private String sourceRef;
    private String diagnoseType;
    private String appId;
    private String env;
    private String citationCode;
    private Integer rank;
    private Double similarity;
    private String content;
}
