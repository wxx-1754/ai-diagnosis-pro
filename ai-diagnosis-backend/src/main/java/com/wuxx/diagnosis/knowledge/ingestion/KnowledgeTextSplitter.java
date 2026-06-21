package com.wuxx.diagnosis.knowledge.ingestion;

import java.util.ArrayList;
import java.util.List;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class KnowledgeTextSplitter {

    private final KnowledgeBaseProperties properties;

    public List<String> split(String title, String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        int max = Math.max(100, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), max / 2));
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + max);
            if (end < normalized.length()) {
                int semanticBreak = findBreak(normalized, start, end);
                if (semanticBreak > start + max / 2) {
                    end = semanticBreak;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add("# " + title.trim() + "\n\n" + piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private int findBreak(String text, int start, int end) {
        for (String separator : List.of("\n\n", "\n#", "。", "；", ";", "\n")) {
            int index = text.lastIndexOf(separator, end);
            if (index > start) {
                return index + separator.length();
            }
        }
        return end;
    }
}
