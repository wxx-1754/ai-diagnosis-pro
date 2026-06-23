package com.wuxx.diagnosis.knowledge.ingestion;

import java.util.List;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.KbChunk;
import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeReviewRequest;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeTextRequest;
import com.wuxx.diagnosis.knowledge.mapper.KbChunkMapper;
import com.wuxx.diagnosis.knowledge.mapper.KbDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIngestionServiceTest {

    private KnowledgeTextSplitter splitter;
    private KbDocumentMapper documentMapper;
    private KbChunkMapper chunkMapper;
    private VectorStore vectorStore;
    private KnowledgeIngestionService service;

    @BeforeEach
    void setUp() {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        properties.setEnabled(true);
        splitter = mock(KnowledgeTextSplitter.class);
        documentMapper = mock(KbDocumentMapper.class);
        chunkMapper = mock(KbChunkMapper.class);
        vectorStore = mock(VectorStore.class);
        service = new KnowledgeIngestionService(
                properties, splitter, documentMapper, chunkMapper, vectorStore);
    }

    @Test
    void historyReportWaitsForReviewWithoutIndexing() {
        KbDocument document = service.createPendingReview(historyRequest(), 512);

        assertEquals("PENDING_REVIEW", document.getQualityStatus());
        assertEquals("PENDING", document.getStatus());
        assertEquals(0, document.getChunkCount());
        verify(documentMapper).insert(document);
        verify(chunkMapper, never()).insert(any());
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void approvalIndexesOnlyReviewedContent() {
        KbDocument pending = pendingDocument();
        when(documentMapper.findByDocNo("KB-1")).thenReturn(pending);
        when(documentMapper.approvePendingReview(any())).thenReturn(1);
        when(splitter.split("修订后的案例", "修订后的可靠结论")).thenReturn(List.of(
                "# 修订后的案例\n\n修订后的可靠结论"));
        doAnswer(invocation -> {
            KbChunk chunk = invocation.getArgument(0);
            chunk.setId(7L);
            return 1;
        }).when(chunkMapper).insert(any());

        KbDocument approved = service.review("KB-1", approveRequest());

        assertEquals("APPROVED", approved.getQualityStatus());
        assertEquals("INDEXED", approved.getStatus());
        assertEquals("修订后的可靠结论", approved.getReviewedContent());
        verify(vectorStore).add(anyList());
        verify(documentMapper).updateIndexState(
                approved.getId(), "INDEXED", 1, null, approved.getIndexedAt(), approved.getIndexedAt());
    }

    @Test
    void rejectionKeepsAuditRecordAndNeverIndexes() {
        KbDocument pending = pendingDocument();
        when(documentMapper.findByDocNo("KB-1")).thenReturn(pending);
        when(documentMapper.rejectPendingReview(any())).thenReturn(1);
        KnowledgeReviewRequest request = new KnowledgeReviewRequest();
        request.setAction("REJECT");
        request.setComment("实时证据不足，结论不可复用");
        request.setReviewedBy("reviewer-a");

        KbDocument rejected = service.review("KB-1", request);

        assertEquals("REJECTED", rejected.getQualityStatus());
        assertEquals("实时证据不足，结论不可复用", rejected.getReviewComment());
        verify(vectorStore, never()).add(anyList());
        verify(chunkMapper, never()).insert(any());
    }

    @Test
    void repeatedReviewIsRejected() {
        KbDocument pending = pendingDocument();
        when(documentMapper.findByDocNo("KB-1")).thenReturn(pending);
        when(documentMapper.approvePendingReview(any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.review("KB-1", approveRequest()));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void approvalRemainsApprovedWhenEmbeddingFails() {
        KbDocument pending = pendingDocument();
        when(documentMapper.findByDocNo("KB-1")).thenReturn(pending);
        when(documentMapper.approvePendingReview(any())).thenReturn(1);
        when(splitter.split("修订后的案例", "修订后的可靠结论")).thenReturn(List.of(
                "# 修订后的案例\n\n修订后的可靠结论"));
        doAnswer(invocation -> {
            KbChunk chunk = invocation.getArgument(0);
            chunk.setId(7L);
            return 1;
        }).when(chunkMapper).insert(any());
        doThrow(new IllegalStateException("embedding unavailable")).when(vectorStore).add(anyList());

        KbDocument approved = service.review("KB-1", approveRequest());

        assertEquals("APPROVED", approved.getQualityStatus());
        assertEquals("FAILED", approved.getStatus());
        verify(documentMapper).updateIndexState(
                approved.getId(), "FAILED", 0, "embedding unavailable", null, approved.getUpdatedAt());
    }

    private KnowledgeTextRequest historyRequest() {
        KnowledgeTextRequest request = new KnowledgeTextRequest();
        request.setTitle("历史诊断报告");
        request.setContent("包含实时证据的历史报告正文");
        request.setCategory("CASE");
        request.setDiagnoseType("SLOW_REQUEST");
        request.setAppId("order-service");
        request.setEnv("prod");
        request.setSourceRef("TASK-1");
        request.setUploadedBy("system");
        return request;
    }

    private KbDocument pendingDocument() {
        KbDocument document = new KbDocument();
        document.setId(1L);
        document.setDocNo("KB-1");
        document.setTitle("原始报告");
        document.setSourceType("HISTORY_REPORT");
        document.setSourceRef("TASK-1");
        document.setRawContent("原始报告内容");
        document.setQualityStatus("PENDING_REVIEW");
        document.setStatus("PENDING");
        document.setVersion(1);
        return document;
    }

    private KnowledgeReviewRequest approveRequest() {
        KnowledgeReviewRequest request = new KnowledgeReviewRequest();
        request.setAction("APPROVE");
        request.setTitle("修订后的案例");
        request.setContent("修订后的可靠结论");
        request.setCategory("CASE");
        request.setDiagnoseType("SLOW_REQUEST");
        request.setAppId("order-service");
        request.setEnv("prod");
        request.setComment("已核对实时证据");
        request.setReviewedBy("reviewer-a");
        return request;
    }
}
