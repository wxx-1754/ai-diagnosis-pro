package com.wuxx.diagnosis.knowledge.controller;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.KbChunk;
import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import com.wuxx.diagnosis.knowledge.domain.KnowledgeTextRequest;
import com.wuxx.diagnosis.knowledge.ingestion.KnowledgeIngestionService;
import com.wuxx.diagnosis.knowledge.security.KbAdminAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/kb/documents")
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class KnowledgeDocumentController {

    private static final String TOKEN_HEADER = "X-Diagnosis-Admin-Token";

    private final KnowledgeIngestionService ingestionService;
    private final KbAdminAccessGuard accessGuard;

    @PostMapping("/text")
    public KbDocument ingestText(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                 @Valid @RequestBody KnowledgeTextRequest request) {
        accessGuard.check(token);
        return ingestionService.ingestText(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KbDocument upload(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                             @RequestParam MultipartFile file,
                             @RequestParam(required = false) String title,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String diagnoseType,
                             @RequestParam(required = false) String appId,
                             @RequestParam(required = false) String env,
                             @RequestParam(required = false) String uploadedBy) {
        accessGuard.check(token);
        return ingestionService.ingestFile(file, title, category, diagnoseType, appId, env, uploadedBy);
    }

    @GetMapping
    public List<KbDocument> list(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                 @RequestParam(required = false) String sourceType,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        accessGuard.check(token);
        return ingestionService.list(sourceType, status, page, size);
    }

    @GetMapping("/{docNo}")
    public KbDocument detail(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                             @PathVariable String docNo) {
        accessGuard.check(token);
        return ingestionService.requireDocument(docNo);
    }

    @GetMapping("/{docNo}/chunks")
    public List<KbChunk> chunks(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                @PathVariable String docNo) {
        accessGuard.check(token);
        return ingestionService.chunks(docNo);
    }

    @PostMapping("/{docNo}/reindex")
    public KbDocument reindex(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                              @PathVariable String docNo) {
        accessGuard.check(token);
        return ingestionService.reindex(docNo);
    }

    @DeleteMapping("/{docNo}")
    public void delete(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                       @PathVariable String docNo) {
        accessGuard.check(token);
        ingestionService.delete(docNo);
    }
}
