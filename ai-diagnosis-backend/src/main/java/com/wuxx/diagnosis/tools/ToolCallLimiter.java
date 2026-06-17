package com.wuxx.diagnosis.tools;

import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolCallLimiter {

    private static final Map<String, Long> LIMITS = Map.of(
            "dashboard", 3L,
            "topThreads", 3L,
            "threadStack", 10L,
            "memoryInfo", 3L,
            "jvmInfo", 3L,
            "traceMethod", 3L
    );

    private final ArthasCommandRecordMapper commandRecordMapper;

    public void check(String taskNo, String toolName) {
        long limit = LIMITS.getOrDefault(toolName, 3L);
        List<ArthasCommandRecord> records = commandRecordMapper.findByTaskNo(taskNo);
        long used = records == null ? 0 : records.stream()
                .filter(record -> toolName.equals(record.getCommandType()))
                .count();
        if (used >= limit) {
            throw new SecurityException("Tool 调用次数超过限制，toolName=" + toolName + "，limit=" + limit);
        }
    }
}
