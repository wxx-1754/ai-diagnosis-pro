package com.wuxx.diagnosis.knowledge.retrieval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeContext;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeSearchRequest;
import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import com.wuxx.diagnosis.knowledge.domain.RetrievedChunk;
import com.wuxx.diagnosis.knowledge.mapper.DiagnoseReportReferenceMapper;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class ReportKnowledgeContextService {

    private final KnowledgeBaseProperties properties;
    private final KnowledgeRetrievalService retrievalService;
    private final DiagnoseReportReferenceMapper referenceMapper;
    private final DiagnoseSseManager sseManager;

    public KnowledgeContext prepare(DiagnoseTask task, String sql) {
        send(task.getTaskNo(), DiagnoseEventType.KNOWLEDGE_RETRIEVING, "正在检索诊断知识库", null);
        KnowledgeSearchRequest request = new KnowledgeSearchRequest();
        request.setQuestion(buildQuestion(task, sql));
        request.setDiagnoseType(task.getDiagnoseType());
        request.setAppId(task.getAppId());
        request.setEnv(task.getEnv());
        request.setTopK(properties.getRetrieveTopK());
        List<RetrievedChunk> chunks = retrievalService.retrieve(request);

        referenceMapper.deleteByTaskNo(task.getTaskNo());
        List<ReportKnowledgeReference> references = new ArrayList<>();
        StringBuilder prompt = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            ReportKnowledgeReference reference = toReference(task.getTaskNo(), chunk);
            referenceMapper.insert(reference);
            references.add(reference);
            if (prompt.length() < properties.getMaxContextLength()) {
                prompt.append("\n----\n[")
                        .append(chunk.getCitationCode())
                        .append("] 来源：")
                        .append(chunk.getTitle())
                        .append("；相似度：")
                        .append(String.format("%.3f", chunk.getSimilarity()))
                        .append("\n")
                        .append(chunk.getContent());
            }
        }
        send(task.getTaskNo(), DiagnoseEventType.KNOWLEDGE_RETRIEVED,
                chunks.isEmpty() ? "未检索到足够相关的知识" : "已检索到 " + chunks.size() + " 条相关知识",
                Map.of("count", chunks.size(),
                        "sources", chunks.stream().map(RetrievedChunk::getTitle).distinct().toList()));
        if (chunks.isEmpty()) {
            return KnowledgeContext.empty();
        }
        return new KnowledgeContext(prompt.toString(), references);
    }

    public void markCitations(String taskNo, String reportMarkdown) {
        referenceMapper.updateUsage(taskNo, reportMarkdown == null ? "" : reportMarkdown);
    }

    public List<ReportKnowledgeReference> references(String taskNo) {
        return referenceMapper.findByTaskNo(taskNo);
    }

    private String buildQuestion(DiagnoseTask task, String sql) {
        StringBuilder query = new StringBuilder();
        append(query, task.getQuestion());
        append(query, task.getDiagnoseType());
        if (StringUtils.hasText(task.getTargetClass()) || StringUtils.hasText(task.getTargetMethod())) {
            append(query, String.valueOf(task.getTargetClass()) + "." + String.valueOf(task.getTargetMethod()));
        }
        append(query, sql);
        return query.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(value);
        }
    }

    private ReportKnowledgeReference toReference(String taskNo, RetrievedChunk chunk) {
        ReportKnowledgeReference reference = new ReportKnowledgeReference();
        reference.setTaskNo(taskNo);
        reference.setChunkId(chunk.getChunkId());
        reference.setDocNo(chunk.getDocNo());
        reference.setCitationCode(chunk.getCitationCode());
        reference.setSourceType(chunk.getSourceType());
        reference.setTitle(chunk.getTitle());
        reference.setSourceRef(chunk.getSourceRef());
        reference.setSimilarity(chunk.getSimilarity());
        reference.setRetrievalRank(chunk.getRank());
        reference.setUsageType("RETRIEVED");
        String content = chunk.getContent();
        reference.setExcerpt(content == null || content.length() <= 1000 ? content : content.substring(0, 1000));
        reference.setCreatedAt(LocalDateTime.now());
        return reference;
    }

    private void send(String taskNo, DiagnoseEventType type, String message, Object data) {
        sseManager.send(taskNo, DiagnoseEvent.builder()
                .taskNo(taskNo)
                .eventType(type.name())
                .message(message)
                .data(data)
                .time(LocalDateTime.now())
                .build());
    }
}
