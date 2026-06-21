package com.wuxx.diagnosis.sql.audit;

import java.time.LocalDateTime;

import com.wuxx.diagnosis.sql.domain.SqlToolCallRecord;
import com.wuxx.diagnosis.sql.mapper.SqlToolCallRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SqlDiagnosisAuditService {

    private static final int EXCERPT_LIMIT = 4000;

    private final SqlToolCallRecordMapper mapper;

    public void record(String taskNo,
                       Long sqlRecordId,
                       String datasourceCode,
                       String toolName,
                       String requestSql,
                       boolean success,
                       long costMillis,
                       String result,
                       String errorMessage) {
        SqlToolCallRecord record = new SqlToolCallRecord();
        record.setTaskNo(taskNo);
        record.setSqlRecordId(sqlRecordId);
        record.setDatasourceCode(datasourceCode);
        record.setToolName(toolName);
        record.setRequestSql(requestSql);
        record.setSuccess(success);
        record.setCostMillis(costMillis);
        record.setResultExcerpt(truncate(result));
        record.setErrorMessage(truncate(errorMessage));
        record.setCreatedAt(LocalDateTime.now());
        mapper.insert(record);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= EXCERPT_LIMIT) {
            return value;
        }
        return value.substring(0, EXCERPT_LIMIT);
    }
}
