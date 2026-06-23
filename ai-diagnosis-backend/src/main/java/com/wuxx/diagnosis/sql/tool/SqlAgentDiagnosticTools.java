package com.wuxx.diagnosis.sql.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.service.DiagnoseTaskService;
import com.wuxx.diagnosis.sql.audit.SqlDiagnosisAuditService;
import com.wuxx.diagnosis.sql.datasource.SqlDatasourceService;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceOption;
import com.wuxx.diagnosis.sql.domain.SqlDiagnosisRecord;
import com.wuxx.diagnosis.sql.explain.MysqlExplainExecutor;
import com.wuxx.diagnosis.sql.explain.SqlExplainResult;
import com.wuxx.diagnosis.sql.mapper.SqlDiagnosisRecordMapper;
import com.wuxx.diagnosis.sql.metadata.MysqlMetadataService;
import com.wuxx.diagnosis.sql.metadata.SqlMetadataBundle;
import com.wuxx.diagnosis.sql.security.SqlDiagnosisEvidenceGate;
import com.wuxx.diagnosis.sql.security.SqlSafetyChecker;
import com.wuxx.diagnosis.sse.DiagnoseEvent;
import com.wuxx.diagnosis.sse.DiagnoseEventType;
import com.wuxx.diagnosis.sse.DiagnoseSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlAgentDiagnosticTools {

    private final DiagnoseTaskService diagnoseTaskService;
    private final SqlDatasourceService datasourceService;
    private final SqlDiagnosisRecordMapper recordMapper;
    private final SqlSafetyChecker safetyChecker;
    private final SqlDiagnosisEvidenceGate evidenceGate;
    private final MysqlExplainExecutor explainExecutor;
    private final MysqlMetadataService metadataService;
    private final SqlDiagnosisAuditService auditService;
    private final DiagnoseSseManager sseManager;

    @Tool(description = """
            列出指定环境已经启用的 SQL 诊断数据源。捕获到 SQL 后，先调用该工具确定 datasourceCode。
            仅当 trace/watch 已经命中 MyBatis、Mapper、Executor 或 JDBC 调用链后才允许调用，
            否则会被证据门禁拒绝；未命中时请先回到 Arthas trace/watch 采样。
            """)
    public List<SqlDatasourceOption> listSqlDatasources(String taskNo, String appId, String env) {
        log.info("listSqlDatasources called, taskNo={}, appId={}, env={}", taskNo, appId, env);
        diagnoseTaskService.checkTaskAppEnv(taskNo, appId, env);
        evidenceGate.verify(taskNo);
        send(taskNo, DiagnoseEventType.SQL_DATASOURCE_SELECTING,
                "Agent 正在匹配 SQL 诊断数据源", Map.of("appId", appId, "env", env));
        List<SqlDatasourceOption> options = datasourceService.options(appId, env);
        if (options.size() == 1) {
            send(taskNo, DiagnoseEventType.SQL_DATASOURCE_SELECTED,
                    "已自动选择数据源：" + options.get(0).getDatasourceName(), options.get(0));
        } else if (options.size() > 1) {
            send(taskNo, DiagnoseEventType.SQL_DATASOURCE_AMBIGUOUS,
                    "检测到多个可用数据源，Agent 将根据名称和 SQL 上下文选择",
                    Map.of("count", options.size(), "options", options));
        } else {
            send(taskNo, DiagnoseEventType.SQL_DATASOURCE_AMBIGUOUS,
                    "当前环境没有可用的 SQL 诊断数据源", Map.of("count", 0));
        }
        return options;
    }

    @Tool(description = """
            对 watch 捕获到的单条 MySQL SELECT SQL 自动执行安全校验、EXPLAIN FORMAT=JSON，
            并采集主表字段、索引和统计信息。SQL 必须已经包含真实参数值，不能包含 ? 或命名占位符。
            datasourceCode 必须来自 listSqlDatasources，mainTableName 是 SQL 的主要查询表。
            仅当 trace/watch 已经命中 MyBatis、Mapper、Executor 或 JDBC 调用链后才允许调用，
            否则会被证据门禁拒绝。
            """)
    public SqlDiagnosisRecord explainCapturedSql(String taskNo,
                                                 String appId,
                                                 String env,
                                                 String datasourceCode,
                                                 String sql,
                                                 String mainTableName) {
        log.info("explainCapturedSql called, taskNo={}, appId={}, env={}, datasourceCode={}, mainTable={}",
                taskNo, appId, env, datasourceCode, mainTableName);
        diagnoseTaskService.checkTaskAppEnv(taskNo, appId, env);
        evidenceGate.verify(taskNo);
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(taskNo);
        SqlDatasourceConfig datasource = datasourceService.getEnabled(datasourceCode, task.getAppId(), task.getEnv());
        String normalizedSql = safetyChecker.checkExplainableSelect(sql);
        String tableName = safetyChecker.checkTableName(mainTableName);
        send(taskNo, DiagnoseEventType.SQL_DATASOURCE_SELECTED,
                "Agent 已选择数据源：" + datasource.getDatasourceName(),
                Map.of("datasourceCode", datasourceCode, "datasourceName", datasource.getDatasourceName()));

        SqlDiagnosisRecord record = new SqlDiagnosisRecord();
        record.setTaskNo(taskNo);
        record.setDatasourceCode(datasourceCode);
        record.setDbType("MYSQL");
        record.setMainTableName(tableName);
        record.setSqlHash(sha256(sql));
        record.setOriginalSql(sql.trim());
        record.setNormalizedSql(normalizedSql);
        record.setStatus("RUNNING");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        recordMapper.insert(record);

        try {
            send(taskNo, DiagnoseEventType.SQL_EXPLAIN_START,
                    "正在对捕获 SQL 执行 MySQL Explain",
                    Map.of("sqlRecordId", record.getId(), "datasourceCode", datasourceCode,
                            "mainTableName", tableName, "sql", sql));
            SqlExplainResult explain = explainExecutor.explain(datasource, normalizedSql);
            record.setExplainSql(explain.getExplainSql());
            record.setExplainResult(explain.getExplainResult());
            auditService.record(taskNo, record.getId(), datasourceCode, "explainSql",
                    explain.getExplainSql(), true, explain.getCostMillis(), explain.getExplainResult(), null);
            send(taskNo, DiagnoseEventType.SQL_EXPLAIN_SUCCESS,
                    "MySQL Explain 执行成功",
                    Map.of("sqlRecordId", record.getId(), "costMillis", explain.getCostMillis(),
                            "explainResult", explain.getExplainResult()));

            send(taskNo, DiagnoseEventType.SQL_META_COLLECTING,
                    "正在采集主表字段、索引和统计信息",
                    Map.of("sqlRecordId", record.getId(), "mainTableName", tableName));
            long metadataStart = System.currentTimeMillis();
            SqlMetadataBundle metadata = metadataService.collect(datasource, tableName);
            record.setTableMetaJson(metadata.getTableMetaJson());
            record.setIndexMetaJson(metadata.getIndexMetaJson());
            record.setTableStatsJson(metadata.getTableStatsJson());
            auditService.record(taskNo, record.getId(), datasourceCode, "collectMetadata",
                    null, true, System.currentTimeMillis() - metadataStart,
                    metadata.getTableMetaJson() + metadata.getIndexMetaJson() + metadata.getTableStatsJson(), null);
            send(taskNo, DiagnoseEventType.SQL_META_COLLECTED,
                    "SQL 元数据采集完成",
                    Map.of("sqlRecordId", record.getId(), "mainTableName", tableName));

            record.setStatus("FINISHED");
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateResult(record);
            return record;
        } catch (Exception exception) {
            record.setStatus("FAILED");
            record.setErrorMessage(exception.getMessage());
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateResult(record);
            auditService.record(taskNo, record.getId(), datasourceCode, "explainCapturedSql",
                    normalizedSql, false, 0, null, exception.getMessage());
            send(taskNo, DiagnoseEventType.SQL_EXPLAIN_FAILED,
                    "自动 SQL Explain 失败：" + exception.getMessage(),
                    Map.of("sqlRecordId", record.getId()));
            throw exception;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SQL指纹生成失败", exception);
        }
    }

    private void send(String taskNo, DiagnoseEventType type, String message, Object data) {
        sseManager.send(taskNo, DiagnoseEvent.builder()
                .taskNo(taskNo)
                .eventType(type.name())
                .message(message)
                .data(data)
                .time(LocalDateTime.now())
                .build());
    }
}
