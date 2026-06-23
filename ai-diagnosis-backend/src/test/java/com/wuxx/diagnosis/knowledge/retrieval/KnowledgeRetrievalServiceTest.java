package com.wuxx.diagnosis.knowledge.retrieval;

import java.util.List;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.KbChunk;
import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeSearchRequest;
import com.wuxx.diagnosis.knowledge.mapper.KbChunkMapper;
import com.wuxx.diagnosis.knowledge.mapper.KbDocumentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {

    @Test
    void pendingReviewDocumentCannotBeRetrievedByFullText() {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        VectorStore vectorStore = mock(VectorStore.class);
        KbChunkMapper chunkMapper = mock(KbChunkMapper.class);
        KbDocumentMapper documentMapper = mock(KbDocumentMapper.class);
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of());

        KbChunk chunk = new KbChunk();
        chunk.setId(1L);
        chunk.setDocNo("KB-PENDING");
        chunk.setVectorId("KB-PENDING:1:0");
        chunk.setContent("连接池等待导致接口变慢");
        when(chunkMapper.fullTextSearch(anyString(), anyInt())).thenReturn(List.of(chunk));

        KbDocument document = new KbDocument();
        document.setDocNo("KB-PENDING");
        document.setQualityStatus("PENDING_REVIEW");
        document.setStatus("PENDING");
        when(documentMapper.findByDocNo("KB-PENDING")).thenReturn(document);

        KnowledgeSearchRequest request = new KnowledgeSearchRequest();
        request.setQuestion("接口慢");
        request.setDiagnoseType("SLOW_REQUEST");
        request.setAppId("order-service");

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                properties, vectorStore, chunkMapper, documentMapper);

        assertTrue(service.retrieve(request).isEmpty());
    }
}

