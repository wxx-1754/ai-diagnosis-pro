package com.wuxx.diagnosis.knowledge.controller;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import com.wuxx.diagnosis.knowledge.mapper.DiagnoseReportReferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/diagnose")
public class KnowledgeReferenceController {

    private final DiagnoseReportReferenceMapper referenceMapper;

    @GetMapping("/{taskNo}/report/references")
    public List<ReportKnowledgeReference> references(@PathVariable String taskNo) {
        return referenceMapper.findByTaskNo(taskNo);
    }
}
