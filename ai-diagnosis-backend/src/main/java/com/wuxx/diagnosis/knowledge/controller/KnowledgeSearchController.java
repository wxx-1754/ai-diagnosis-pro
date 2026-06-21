package com.wuxx.diagnosis.knowledge.controller;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.KnowledgeSearchRequest;
import com.wuxx.diagnosis.knowledge.domain.RetrievedChunk;
import com.wuxx.diagnosis.knowledge.retrieval.KnowledgeRetrievalService;
import com.wuxx.diagnosis.knowledge.security.KbAdminAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/kb")
@ConditionalOnProperty(prefix = "diagnosis.kb", name = "enabled", havingValue = "true")
public class KnowledgeSearchController {

    private static final String TOKEN_HEADER = "X-Diagnosis-Admin-Token";

    private final KnowledgeRetrievalService retrievalService;
    private final KbAdminAccessGuard accessGuard;

    @PostMapping("/search")
    public List<RetrievedChunk> search(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                       @Valid @RequestBody KnowledgeSearchRequest request) {
        accessGuard.check(token);
        return retrievalService.retrieve(request);
    }
}
