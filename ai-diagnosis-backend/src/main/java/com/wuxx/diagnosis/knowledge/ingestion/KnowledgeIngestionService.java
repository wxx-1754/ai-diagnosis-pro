package com.wuxx.diagnosis.knowledge.ingestion;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import com.wuxx.diagnosis.knowledge.domain.KbChunk;
import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeTextRequest;
import com.wuxx.diagnosis.knowledge.mapper.KbChunkMapper;
import com.wuxx.diagnosis.knowledge.mapper.KbDocumentMapper;
import com.wuxx.diagnosis.service.ai.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class KnowledgeIngestionService {

    private static final String GLOBAL = "__GLOBAL__";

    private final KnowledgeBaseProperties properties;
    private final KnowledgeTextSplitter splitter;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final VectorStore vectorStore;

    @EventListener(ApplicationReadyEvent.class)
    public void loadPersistedVectorStore() {
        if (!(vectorStore instanceof SimpleVectorStore simpleVectorStore)) {
            return;
        }
        File file = vectorFile();
        if (!file.isFile()) {
            return;
        }
        try {
            simpleVectorStore.load(file);
            log.info("Knowledge vector store loaded, file={}", file.getAbsolutePath());
        } catch (Exception exception) {
            log.warn("Failed to load knowledge vector store, file={}, message={}",
                    file.getAbsolutePath(), exception.getMessage());
        }
    }

    public KbDocument ingestFile(MultipartFile file,
                                 String title,
                                 String category,
                                 String diagnoseType,
                                 String appId,
                                 String env,
                                 String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("知识文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!isSupported(filename)) {
            throw new IllegalArgumentException("一期仅支持 .md、.markdown 和 .txt 文件");
        }
        try {
            KnowledgeTextRequest request = new KnowledgeTextRequest();
            request.setTitle(StringUtils.hasText(title) ? title : filename);
            request.setContent(new String(file.getBytes(), StandardCharsets.UTF_8));
            request.setCategory(category);
            request.setDiagnoseType(diagnoseType);
            request.setAppId(appId);
            request.setEnv(env);
            request.setSourceRef(filename);
            request.setUploadedBy(uploadedBy);
            return ingest(request, "MANUAL", file.getSize());
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("知识文件读取失败", exception);
        }
    }

    public KbDocument ingestText(KnowledgeTextRequest request) {
        return ingest(request, "MANUAL",
                request.getContent().getBytes(StandardCharsets.UTF_8).length);
    }

    /**
     * 入库核心方法，供手工文本/文件上传与历史报告沉淀复用。
     *
     * @param sourceType MANUAL / HISTORY_REPORT
     */
    @Transactional
    public KbDocument ingest(KnowledgeTextRequest request, String sourceType, long fileSize) {
        requireEnabled();
        String masked = SensitiveDataMasker.mask(request.getContent());
        String contentHash = sha256(masked);
        KbDocument existing = documentMapper.findByContentHash(contentHash);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        KbDocument document = new KbDocument();
        document.setDocNo("KB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        document.setTitle(request.getTitle().trim());
        document.setSourceType(sourceType);
        document.setCategory(defaultValue(request.getCategory(), "SOP"));
        document.setDiagnoseType(normalizeScope(request.getDiagnoseType()));
        document.setAppId(normalizeScope(request.getAppId()));
        document.setEnv(normalizeScope(request.getEnv()));
        document.setSourceRef(request.getSourceRef());
        document.setContentHash(contentHash);
        document.setVersion(1);
        document.setQualityStatus("APPROVED");
        document.setStatus("INDEXING");
        document.setChunkCount(0);
        document.setFileSize(fileSize);
        document.setEmbeddingModel(properties.getEmbeddingModel());
        document.setEmbeddingDimension(properties.getEmbeddingDimension());
        document.setUploadedBy(request.getUploadedBy());
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        return indexChunks(document, masked, 1);
    }

    /**
     * 重建已有文档的索引：丢弃旧分片与向量，按当前分片参数重新切分并 embedding。
     *
     * <p>原始内容不单独存储，而是从旧分片中还原（每个分片入库时统一加了
     * {@code "# " + title + "\n\n"} 前缀，去掉前缀后拼接即为原文）。
     * version 递增，使新 vectorId 与旧的区分，避免向量库残留。
     */
    @Transactional
    public KbDocument reindex(String docNo) {
        requireEnabled();
        KbDocument document = requireDocument(docNo);
        List<KbChunk> oldChunks = chunkMapper.findByDocId(document.getId());
        List<String> oldVectorIds = oldChunks.stream().map(KbChunk::getVectorId).toList();

        String prefix = "# " + document.getTitle().trim() + "\n\n";
        String raw = oldChunks.stream()
                .map(KbChunk::getContent)
                .map(content -> content.startsWith(prefix) ? content.substring(prefix.length()) : content)
                .collect(java.util.stream.Collectors.joining("\n"));

        if (!oldVectorIds.isEmpty()) {
            try {
                vectorStore.delete(oldVectorIds);
                persistVectorStore();
            } catch (Exception exception) {
                log.warn("Reindex old vector cleanup failed, docNo={}, message={}",
                        docNo, exception.getMessage());
            }
        }
        chunkMapper.deleteByDocId(document.getId());

        int newVersion = (document.getVersion() == null ? 0 : document.getVersion()) + 1;
        document.setVersion(newVersion);
        return indexChunks(document, raw, newVersion);
    }

    /**
     * 切分、embedding、落分片，并更新文档索引状态。ingest 与 reindex 共用。
     */
    private KbDocument indexChunks(KbDocument document, String content, int version) {
        List<String> pieces = splitter.split(document.getTitle(), content);
        if (pieces.isEmpty()) {
            if (document.getId() != null && version == 1) {
                documentMapper.hardDelete(document.getId());
            }
            throw new IllegalArgumentException("知识内容为空或无法分片");
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> vectorIds = new ArrayList<>();
        try {
            List<Document> vectorDocuments = new ArrayList<>();
            for (int index = 0; index < pieces.size(); index++) {
                String vectorId = document.getDocNo() + ":" + version + ":" + index;
                String piece = pieces.get(index);
                KbChunk chunk = new KbChunk();
                chunk.setDocId(document.getId());
                chunk.setDocNo(document.getDocNo());
                chunk.setChunkIndex(index);
                chunk.setChunkHash(sha256(piece));
                chunk.setContent(piece);
                chunk.setVectorId(vectorId);
                chunk.setTokenCount(estimateTokens(piece));
                chunk.setCreatedAt(now);
                chunkMapper.insert(chunk);
                vectorIds.add(vectorId);
                vectorDocuments.add(new Document(vectorId, piece, Map.of(
                        "chunkId", chunk.getId(),
                        "docNo", document.getDocNo(),
                        "title", document.getTitle(),
                        "sourceType", document.getSourceType(),
                        "sourceRef", defaultValue(document.getSourceRef(), ""),
                        "diagnoseType", document.getDiagnoseType(),
                        "appId", document.getAppId(),
                        "env", document.getEnv()
                )));
            }
            vectorStore.add(vectorDocuments);
            persistVectorStore();
            LocalDateTime indexedAt = LocalDateTime.now();
            documentMapper.updateIndexState(document.getId(), "INDEXED", pieces.size(), null, indexedAt, indexedAt);
            if (version > 1) {
                documentMapper.updateVersion(document.getId(), version, indexedAt);
            }
            document.setStatus("INDEXED");
            document.setChunkCount(pieces.size());
            document.setIndexedAt(indexedAt);
            document.setUpdatedAt(indexedAt);
            return document;
        } catch (Exception exception) {
            if (!vectorIds.isEmpty()) {
                try {
                    vectorStore.delete(vectorIds);
                    persistVectorStore();
                } catch (Exception cleanupException) {
                    log.warn("Knowledge vector cleanup failed, docNo={}, message={}",
                            document.getDocNo(), cleanupException.getMessage());
                }
            }
            documentMapper.updateIndexState(document.getId(), "FAILED", 0,
                    truncate(exception.getMessage(), 1000), null, LocalDateTime.now());
            throw new IllegalStateException("知识索引失败：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public void delete(String docNo) {
        requireEnabled();
        KbDocument document = requireDocument(docNo);
        List<KbChunk> chunks = chunkMapper.findByDocId(document.getId());
        vectorStore.delete(chunks.stream().map(KbChunk::getVectorId).toList());
        persistVectorStore();
        chunkMapper.deleteByDocId(document.getId());
        documentMapper.markDeleted(document.getId(), LocalDateTime.now());
    }

    public KbDocument requireDocument(String docNo) {
        KbDocument document = documentMapper.findByDocNo(docNo);
        if (document == null) {
            throw new IllegalArgumentException("知识文档不存在，docNo=" + docNo);
        }
        return document;
    }

    public List<KbChunk> chunks(String docNo) {
        return chunkMapper.findByDocId(requireDocument(docNo).getId());
    }

    public List<KbDocument> list(String sourceType, String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * safeSize;
        return documentMapper.list(sourceType, status, offset, safeSize);
    }

    private void persistVectorStore() {
        if (!(vectorStore instanceof SimpleVectorStore simpleVectorStore)) {
            return;
        }
        File file = vectorFile();
        try {
            Path parent = file.toPath().toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            simpleVectorStore.save(file);
        } catch (Exception exception) {
            throw new IllegalStateException("知识向量文件保存失败", exception);
        }
    }

    private File vectorFile() {
        return Path.of(properties.getVectorStoreFile()).toAbsolutePath().toFile();
    }

    private boolean isSupported(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt");
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("知识库未启用");
        }
    }

    private String normalizeScope(String value) {
        return StringUtils.hasText(value) ? value.trim() : GLOBAL;
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 1.8));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("知识内容 hash 生成失败", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
