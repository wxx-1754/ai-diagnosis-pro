package com.wuxx.diagnosis.knowledge.retrieval;

import java.util.List;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.RetrievedChunk;
import com.wuxx.diagnosis.knowledge.mapper.DiagnoseReportReferenceMapper;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportKnowledgeContextServiceTest {

    @Test
    void persistsRetrievedEvidenceAndMarksCitations() {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        properties.setRetrieveTopK(5);
        properties.setMaxContextLength(5000);
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        DiagnoseReportReferenceMapper referenceMapper = mock(DiagnoseReportReferenceMapper.class);
        DiagnoseSseManager sseManager = mock(DiagnoseSseManager.class);
        when(retrievalService.retrieve(any())).thenReturn(List.of(
                RetrievedChunk.builder()
                        .chunkId(7L)
                        .docNo("KB-1")
                        .title("线程池排障手册")
                        .sourceType("MANUAL")
                        .sourceRef("thread-pool.md")
                        .citationCode("K1")
                        .rank(1)
                        .similarity(0.82)
                        .content("线程池队列持续增长时，应检查消费者处理耗时。")
                        .build()
        ));
        ReportKnowledgeContextService service = new ReportKnowledgeContextService(
                properties, retrievalService, referenceMapper, sseManager);
        DiagnoseTask task = new DiagnoseTask();
        task.setTaskNo("T-1");
        task.setQuestion("接口慢");
        task.setDiagnoseType("SLOW_REQUEST");
        task.setAppId("order-service");
        task.setEnv("test");

        var context = service.prepare(task, null);
        service.markCitations("T-1", "根因与线程池拥塞一致。[K1]");

        assertEquals(1, context.references().size());
        assertTrue(context.promptContext().contains("[K1]"));
        verify(referenceMapper).deleteByTaskNo("T-1");
        verify(referenceMapper).insert(any());
        verify(referenceMapper).updateUsage("T-1", "根因与线程池拥塞一致。[K1]");
    }
}
