package com.wuxx.diagnosis.sql.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import com.wuxx.diagnosis.domain.DiagnoseReport;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskStatus;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.domain.ai.DiagnosisInsightSummary;
import com.wuxx.diagnosis.domain.ai.DiagnosisReportPayload;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.mapper.DiagnoseReportMapper;
import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import com.wuxx.diagnosis.knowledge.mapper.DiagnoseReportReferenceMapper;
import com.wuxx.diagnosis.service.DiagnoseReportService;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.service.ai.DiagnosisInsightSummarizer;
import com.wuxx.diagnosis.sql.ai.JavaSqlJointReportGenerator;
import com.wuxx.diagnosis.sql.audit.SqlDiagnosisAuditService;
import com.wuxx.diagnosis.sql.datasource.SqlDatasourceService;
import com.wuxx.diagnosis.sql.domain.JavaSqlJointDiagnosisRequest;
import com.wuxx.diagnosis.sql.domain.JavaSqlJointDiagnosisResponse;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import com.wuxx.diagnosis.sql.domain.SqlDiagnosisRecord;
import com.wuxx.diagnosis.sql.explain.MysqlExplainExecutor;
import com.wuxx.diagnosis.sql.explain.SqlExplainResult;
import com.wuxx.diagnosis.sql.mapper.SqlDiagnosisRecordMapper;
import com.wuxx.diagnosis.sql.metadata.MysqlMetadataService;
import com.wuxx.diagnosis.sql.metadata.SqlMetadataBundle;
import com.wuxx.diagnosis.sql.security.SqlSafetyChecker;
import com.wuxx.diagnosis.sql.security.SqlSensitiveDataMasker;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "diagnosis.ai", name = "enable", havingValue = "true")
public class JavaSqlJointDiagnosisService {

    private final DiagnoseTaskService diagnoseTaskService;
    private final SqlDatasourceService datasourceService;
    private final SqlDiagnosisRecordMapper recordMapper;
    private final SqlSafetyChecker safetyChecker;
    private final MysqlExplainExecutor explainExecutor;
    private final MysqlMetadataService metadataService;
    private final SqlDiagnosisAuditService auditService;
    private final JavaSqlJointReportGenerator reportGenerator;
    private final DiagnosisInsightSummarizer insightSummarizer;
    private final DiagnoseReportService reportService;
    private final DiagnoseReportMapper reportMapper;
    private final ArthasCommandRecordMapper arthasCommandRecordMapper;
    private final SqlSensitiveDataMasker sensitiveDataMasker;
    private final DiagnoseSseManager sseManager;
    private final ObjectMapper objectMapper;
    private final DiagnosisAiProperties aiProperties;
    private final Executor diagnosisExecutor;
    private final Map<String, Object> taskLocks = new ConcurrentHashMap<>();
    private DiagnoseReportReferenceMapper reportReferenceMapper;

    @Autowired(required = false)
    public void setReportReferenceMapper(DiagnoseReportReferenceMapper reportReferenceMapper) {
        this.reportReferenceMapper = reportReferenceMapper;
    }

    public JavaSqlJointDiagnosisService(DiagnoseTaskService diagnoseTaskService,
                                        SqlDatasourceService datasourceService,
                                        SqlDiagnosisRecordMapper recordMapper,
                                        SqlSafetyChecker safetyChecker,
                                        MysqlExplainExecutor explainExecutor,
                                        MysqlMetadataService metadataService,
                                        SqlDiagnosisAuditService auditService,
                                        JavaSqlJointReportGenerator reportGenerator,
                                        DiagnosisInsightSummarizer insightSummarizer,
                                        DiagnoseReportService reportService,
                                        DiagnoseReportMapper reportMapper,
                                        ArthasCommandRecordMapper arthasCommandRecordMapper,
                                        SqlSensitiveDataMasker sensitiveDataMasker,
                                        DiagnoseSseManager sseManager,
                                        ObjectMapper objectMapper,
                                        DiagnosisAiProperties aiProperties,
                                        @Qualifier("diagnosisExecutor") Executor diagnosisExecutor) {
        this.diagnoseTaskService = diagnoseTaskService;
        this.datasourceService = datasourceService;
        this.recordMapper = recordMapper;
        this.safetyChecker = safetyChecker;
        this.explainExecutor = explainExecutor;
        this.metadataService = metadataService;
        this.auditService = auditService;
        this.reportGenerator = reportGenerator;
        this.insightSummarizer = insightSummarizer;
        this.reportService = reportService;
        this.reportMapper = reportMapper;
        this.arthasCommandRecordMapper = arthasCommandRecordMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.sseManager = sseManager;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
        this.diagnosisExecutor = diagnosisExecutor;
    }

    public JavaSqlJointDiagnosisResponse start(JavaSqlJointDiagnosisRequest request) {
        DiagnoseTask task = validateTask(request.getTaskNo());
        SqlDatasourceConfig datasource = datasourceService.getEnabled(request.getDatasourceCode().trim(), task.getAppId(), task.getEnv());
        String tableName = safetyChecker.checkTableName(request.getMainTableName());
        SqlDiagnosisRecord record;
        Object lock = taskLocks.computeIfAbsent(task.getTaskNo(), ignored -> new Object());
        synchronized (lock) {
            if (recordMapper.countActiveByTaskNo(task.getTaskNo()) > 0) {
                throw new IllegalStateException("该任务已有进行中的 SQL 联合诊断");
            }
            try {
                record = createRecord(task, datasource, request.getSql(), tableName);
            } catch (DuplicateKeyException exception) {
                throw new IllegalStateException("该任务已有进行中的 SQL 联合诊断", exception);
            }
        }
        try {
            diagnosisExecutor.execute(() -> runAsync(task.getTaskNo(), record.getId()));
        } catch (RuntimeException exception) {
            fail(record, "SQL联合诊断提交失败：" + exception.getMessage());
            taskLocks.remove(task.getTaskNo(), lock);
            throw exception;
        }
        return JavaSqlJointDiagnosisResponse.builder()
                .taskNo(task.getTaskNo())
                .sqlRecordId(record.getId())
                .status(record.getStatus())
                .streamUrl("/api/diagnose/tasks/" + task.getTaskNo() + "/stream")
                .build();
    }

    private void runAsync(String taskNo, Long recordId) {
        SqlDiagnosisRecord record = recordMapper.findById(recordId);
        try {
            record.setStatus("RUNNING");
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateResult(record);

            long safetyStart = System.currentTimeMillis();
            String normalizedSql;
            try {
                normalizedSql = safetyChecker.checkExplainableSelect(record.getOriginalSql());
                record.setNormalizedSql(normalizedSql);
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "safetyCheck",
                        record.getOriginalSql(), true, elapsed(safetyStart), "SQL安全校验通过", null);
            } catch (Exception exception) {
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "safetyCheck",
                        record.getOriginalSql(), false, elapsed(safetyStart), null, exception.getMessage());
                throw exception;
            }

            DiagnoseTask task = validateTask(taskNo);
            SqlDatasourceConfig datasource = datasourceService.getEnabled(record.getDatasourceCode(), task.getAppId(), task.getEnv());
            send(taskNo, DiagnoseEventType.SQL_EXPLAIN_START, "正在执行 MySQL Explain",
                    Map.of("sqlRecordId", recordId, "datasourceCode", record.getDatasourceCode()));

            long explainStart = System.currentTimeMillis();
            SqlExplainResult explain;
            try {
                explain = explainExecutor.explain(datasource, normalizedSql);
                record.setExplainSql(explain.getExplainSql());
                record.setExplainResult(explain.getExplainResult());
                record.setUpdatedAt(LocalDateTime.now());
                recordMapper.updateResult(record);
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "explainSql",
                        explain.getExplainSql(), true, explain.getCostMillis(), explain.getExplainResult(), null);
                send(taskNo, DiagnoseEventType.SQL_EXPLAIN_SUCCESS, "MySQL Explain 执行成功",
                        Map.of("sqlRecordId", recordId, "costMillis", explain.getCostMillis()));
            } catch (Exception exception) {
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "explainSql",
                        normalizedSql, false, elapsed(explainStart), null, exception.getMessage());
                send(taskNo, DiagnoseEventType.SQL_EXPLAIN_FAILED, "MySQL Explain 执行失败：" + exception.getMessage(),
                        Map.of("sqlRecordId", recordId));
                throw exception;
            }

            send(taskNo, DiagnoseEventType.SQL_META_COLLECTING, "正在采集主表结构、索引与统计信息",
                    Map.of("sqlRecordId", recordId, "mainTableName", record.getMainTableName()));
            long metaStart = System.currentTimeMillis();
            try {
                SqlMetadataBundle metadata = metadataService.collect(datasource, record.getMainTableName());
                record.setTableMetaJson(metadata.getTableMetaJson());
                record.setIndexMetaJson(metadata.getIndexMetaJson());
                record.setTableStatsJson(metadata.getTableStatsJson());
                record.setUpdatedAt(LocalDateTime.now());
                recordMapper.updateResult(record);
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "getTableMeta",
                        null, true, elapsed(metaStart), metadata.getTableMetaJson(), null);
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "getIndexInfo",
                        null, true, elapsed(metaStart), metadata.getIndexMetaJson(), null);
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "getTableStats",
                        null, true, elapsed(metaStart), metadata.getTableStatsJson(), null);
                send(taskNo, DiagnoseEventType.SQL_META_COLLECTED, "SQL 元数据采集完成",
                        Map.of("sqlRecordId", recordId, "mainTableName", record.getMainTableName()));
            } catch (Exception exception) {
                auditService.record(taskNo, recordId, record.getDatasourceCode(), "collectMetadata",
                        null, false, elapsed(metaStart), null, exception.getMessage());
                send(taskNo, DiagnoseEventType.SQL_META_FAILED, "SQL 元数据采集失败：" + exception.getMessage(),
                        Map.of("sqlRecordId", recordId));
                throw exception;
            }

            send(taskNo, DiagnoseEventType.JOINT_REPORT_GENERATING, "正在生成 Java + SQL 联合诊断报告",
                    Map.of("sqlRecordId", recordId));
            DiagnoseReport previousReport = reportMapper.findByTaskNo(taskNo);
            List<ArthasCommandRecord> arthasRecords = arthasCommandRecordMapper.findByTaskNo(taskNo);
            String jointReport = reportGenerator.generate(
                    task,
                    arthasRecords,
                    previousReport,
                    record,
                    sensitiveDataMasker.maskForAi(record.getOriginalSql())
            );
            DiagnosisInsightSummary insight = insightSummarizer.summarize(jointReport);
            reportService.saveOrUpdate(taskNo, "Java + SQL 联合诊断报告", jointReport,
                    serialize(insight), aiProperties.getChatModel(), aiProperties.getPromptVersion() + "-java-sql");
            diagnoseTaskService.markFinished(taskNo, insight.getRootCause());
            record.setDiagnosisResult(jointReport);
            record.setStatus("FINISHED");
            record.setErrorMessage(null);
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateResult(record);

            DiagnosisReportPayload payload = DiagnosisReportPayload.builder()
                    .reportMarkdown(jointReport)
                    .insightSummary(insight)
                    .references(references(taskNo))
                    .build();
            send(taskNo, DiagnoseEventType.JOINT_REPORT_GENERATED, "Java + SQL 联合诊断报告已生成", payload);
        } catch (Exception exception) {
            log.error("Java SQL joint diagnosis failed, taskNo={}, sqlRecordId={}", taskNo, recordId, exception);
            fail(record, exception.getMessage());
            send(taskNo, DiagnoseEventType.JOINT_REPORT_FAILED,
                    "SQL 联合诊断失败，已保留原 Java 报告：" + exception.getMessage(),
                    Map.of("sqlRecordId", recordId));
        } finally {
            taskLocks.remove(taskNo);
            sseManager.complete(taskNo);
        }
    }

    private DiagnoseTask validateTask(String taskNo) {
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        if (!DiagnoseTaskStatus.FINISHED.name().equals(task.getStatus())) {
            throw new IllegalStateException("只有已完成的诊断任务才能进入 SQL 联合诊断");
        }
        if (!DiagnoseType.SLOW_REQUEST.name().equals(task.getDiagnoseType())) {
            throw new IllegalArgumentException("只有 SLOW_REQUEST 任务支持 SQL 联合诊断");
        }
        return task;
    }

    private SqlDiagnosisRecord createRecord(DiagnoseTask task,
                                            SqlDatasourceConfig datasource,
                                            String sql,
                                            String tableName) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL不能为空");
        }
        SqlDiagnosisRecord record = new SqlDiagnosisRecord();
        record.setTaskNo(task.getTaskNo());
        record.setDatasourceCode(datasource.getDatasourceCode());
        record.setDbType(datasource.getDbType());
        record.setMainTableName(tableName);
        record.setSqlHash(sha256(sql));
        record.setOriginalSql(sql.trim());
        record.setStatus("CREATED");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        recordMapper.insert(record);
        return record;
    }

    private void fail(SqlDiagnosisRecord record, String message) {
        record.setStatus("FAILED");
        record.setErrorMessage(message == null ? "SQL联合诊断失败" : message);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateResult(record);
    }

    private void send(String taskNo, DiagnoseEventType eventType, String message, Object data) {
        sseManager.send(taskNo, DiagnoseEvent.builder()
                .taskNo(taskNo)
                .eventType(eventType.name())
                .message(message)
                .data(data)
                .time(LocalDateTime.now())
                .build());
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private String serialize(DiagnosisInsightSummary insight) {
        try {
            return objectMapper.writeValueAsString(insight);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SQL指纹生成失败", exception);
        }
    }

    private List<ReportKnowledgeReference> references(String taskNo) {
        return reportReferenceMapper == null ? List.of() : reportReferenceMapper.findByTaskNo(taskNo);
    }
}
