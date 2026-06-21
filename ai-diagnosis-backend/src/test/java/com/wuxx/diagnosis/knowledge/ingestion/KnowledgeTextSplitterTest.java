package com.wuxx.diagnosis.knowledge.ingestion;

import java.util.List;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeTextSplitterTest {

    @Test
    void splitsLongTextWithTitleAndOverlap() {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        properties.setChunkSize(100);
        properties.setChunkOverlap(20);
        KnowledgeTextSplitter splitter = new KnowledgeTextSplitter(properties);

        String content = "第一段。".repeat(30) + "\n\n" + "第二段。".repeat(30);
        List<String> chunks = splitter.split("CPU 排障 SOP", content);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.startsWith("# CPU 排障 SOP")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() > 20));
    }

    @Test
    void returnsEmptyForBlankContent() {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        KnowledgeTextSplitter splitter = new KnowledgeTextSplitter(properties);

        assertEquals(List.of(), splitter.split("空文档", "   "));
    }
}
