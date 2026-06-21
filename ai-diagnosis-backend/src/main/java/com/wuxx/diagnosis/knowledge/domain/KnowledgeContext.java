package com.wuxx.diagnosis.knowledge.domain;

import java.util.List;

public record KnowledgeContext(String promptContext, List<ReportKnowledgeReference> references) {

    public static KnowledgeContext empty() {
        return new KnowledgeContext("未检索到足够相关且可靠的知识片段。", List.of());
    }
}
