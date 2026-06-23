package com.wuxx.diagnosis.knowledge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.KbChunk;
import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeSearchRequest;
import com.wuxx.diagnosis.knowledge.domain.RetrievedChunk;
import com.wuxx.diagnosis.knowledge.mapper.KbChunkMapper;
import com.wuxx.diagnosis.knowledge.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class KnowledgeRetrievalService {

    private static final String GLOBAL = "__GLOBAL__";

    private final KnowledgeBaseProperties properties;
    private final VectorStore vectorStore;
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;

    public List<RetrievedChunk> retrieve(KnowledgeSearchRequest request) {
        int topK = request.getTopK() == null ? properties.getRetrieveTopK() : request.getTopK();
        topK = Math.max(1, Math.min(topK, 20));
        int candidates = Math.max(topK, topK * Math.max(1, properties.getCandidateMultiplier()));
        SearchRequest search = SearchRequest.builder()
                .query(buildQuery(request))
                .topK(candidates)
                .similarityThresholdAll()
                .build();

        Map<String, Candidate> merged = new LinkedHashMap<>();
        for (Document document : vectorStore.similaritySearch(search)) {
            if (!scopeMatches(document.getMetadata(), request)) {
                continue;
            }
            KbDocument storedDocument = documentMapper.findByDocNo(
                    String.valueOf(document.getMetadata().get("docNo")));
            if (!isSearchable(storedDocument)) {
                continue;
            }
            double vectorScore = document.getScore() == null ? 0.0 : document.getScore();
            merged.put(document.getId(), Candidate.fromVector(document, vectorScore));
        }

        try {
            for (KbChunk chunk : chunkMapper.fullTextSearch(request.getQuestion(), candidates)) {
                KbDocument document = documentMapper.findByDocNo(chunk.getDocNo());
                if (!isSearchable(document) || !scopeMatches(document, request)) {
                    continue;
                }
                merged.compute(chunk.getVectorId(), (key, current) -> {
                    if (current == null) {
                        return Candidate.fromKeyword(chunk, document);
                    }
                    current.keywordMatched = true;
                    return current;
                });
            }
        } catch (RuntimeException exception) {
            log.debug("Knowledge full-text retrieval skipped, message={}", exception.getMessage());
        }

        List<Candidate> ranked = new ArrayList<>(merged.values());
        ranked.forEach(candidate -> candidate.finalScore = score(candidate, request));
        ranked.removeIf(candidate -> candidate.finalScore < properties.getMinScore());
        ranked.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.finalScore).reversed());

        List<RetrievedChunk> results = new ArrayList<>();
        for (int index = 0; index < Math.min(topK, ranked.size()); index++) {
            Candidate candidate = ranked.get(index);
            results.add(RetrievedChunk.builder()
                    .chunkId(candidate.chunkId)
                    .docNo(candidate.docNo)
                    .title(candidate.title)
                    .sourceType(candidate.sourceType)
                    .sourceRef(candidate.sourceRef)
                    .diagnoseType(candidate.diagnoseType)
                    .appId(candidate.appId)
                    .env(candidate.env)
                    .citationCode("K" + (index + 1))
                    .rank(index + 1)
                    .similarity(candidate.finalScore)
                    .content(candidate.content)
                    .build());
        }
        return results;
    }

    private String buildQuery(KnowledgeSearchRequest request) {
        return String.join("\n",
                request.getQuestion(),
                nullToEmpty(request.getDiagnoseType()),
                nullToEmpty(request.getAppId()));
    }

    private double score(Candidate candidate, KnowledgeSearchRequest request) {
        double score = candidate.vectorScore;
        if (candidate.keywordMatched) {
            score += 0.12;
        }
        if (StringUtils.hasText(request.getDiagnoseType())
                && request.getDiagnoseType().equalsIgnoreCase(candidate.diagnoseType)) {
            score += 0.08;
        }
        if (StringUtils.hasText(request.getAppId())
                && request.getAppId().equalsIgnoreCase(candidate.appId)) {
            score += 0.05;
        }
        return Math.min(1.0, score);
    }

    private boolean scopeMatches(Map<String, Object> metadata, KnowledgeSearchRequest request) {
        return matches((String) metadata.get("diagnoseType"), request.getDiagnoseType())
                && matches((String) metadata.get("appId"), request.getAppId())
                && matches((String) metadata.get("env"), request.getEnv());
    }

    private boolean scopeMatches(KbDocument document, KnowledgeSearchRequest request) {
        return matches(document.getDiagnoseType(), request.getDiagnoseType())
                && matches(document.getAppId(), request.getAppId())
                && matches(document.getEnv(), request.getEnv());
    }

    private boolean isSearchable(KbDocument document) {
        return document != null
                && "APPROVED".equals(document.getQualityStatus())
                && "INDEXED".equals(document.getStatus());
    }

    private boolean matches(String stored, String requested) {
        return !StringUtils.hasText(stored)
                || GLOBAL.equals(stored)
                || !StringUtils.hasText(requested)
                || stored.equalsIgnoreCase(requested);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Candidate {
        private Long chunkId;
        private String docNo;
        private String title;
        private String sourceType;
        private String sourceRef;
        private String diagnoseType;
        private String appId;
        private String env;
        private String content;
        private double vectorScore;
        private boolean keywordMatched;
        private double finalScore;

        private static Candidate fromVector(Document document, double score) {
            Candidate candidate = new Candidate();
            Map<String, Object> metadata = document.getMetadata();
            candidate.chunkId = toLong(metadata.get("chunkId"));
            candidate.docNo = String.valueOf(metadata.get("docNo"));
            candidate.title = String.valueOf(metadata.get("title"));
            candidate.sourceType = String.valueOf(metadata.get("sourceType"));
            candidate.sourceRef = String.valueOf(metadata.get("sourceRef"));
            candidate.diagnoseType = String.valueOf(metadata.get("diagnoseType"));
            candidate.appId = String.valueOf(metadata.get("appId"));
            candidate.env = String.valueOf(metadata.get("env"));
            candidate.content = document.getText();
            candidate.vectorScore = score;
            return candidate;
        }

        private static Candidate fromKeyword(KbChunk chunk, KbDocument document) {
            Candidate candidate = new Candidate();
            candidate.chunkId = chunk.getId();
            candidate.docNo = document.getDocNo();
            candidate.title = document.getTitle();
            candidate.sourceType = document.getSourceType();
            candidate.sourceRef = document.getSourceRef();
            candidate.diagnoseType = document.getDiagnoseType();
            candidate.appId = document.getAppId();
            candidate.env = document.getEnv();
            candidate.content = chunk.getContent();
            candidate.vectorScore = 0.48;
            candidate.keywordMatched = true;
            return candidate;
        }

        private static Long toLong(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.valueOf(String.valueOf(value));
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
