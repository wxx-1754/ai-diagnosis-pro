package com.wuxx.diagnosis.knowledge.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KbChunk {
    private Long id;
    private Long docId;
    private String docNo;
    private Integer chunkIndex;
    private String chunkHash;
    private String content;
    private String vectorId;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
