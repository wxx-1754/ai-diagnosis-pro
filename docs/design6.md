# AI + Arthas 诊断 Agent 后续阶段设计文档：接入 SQL Explain，形成 Java + SQL 联合诊断

## 1. 阶段目标

在前 1～6 步基础上，本阶段目标是接入 SQL Explain 能力，形成：

```text
Java 运行时诊断 + SQL 执行计划诊断
```

也就是从单纯的：

```text
Arthas 诊断 JVM / 线程 / 方法耗时
```

升级为：

```text
Arthas 定位 Java 慢方法
  ↓
识别慢方法中的 SQL / Mapper
  ↓
执行 SQL Explain
  ↓
分析执行计划
  ↓
AI 生成 Java + SQL 联合诊断报告
```

本阶段重点解决接口慢场景：

```text
用户反馈接口慢
  ↓
Arthas trace 定位到 Controller / Service / Mapper 耗时
  ↓
如果耗时集中在 Mapper / DAO / JDBC 层
  ↓
进入 SQL Explain 诊断
  ↓
结合 SQL 执行计划、表结构、索引信息、统计信息生成优化建议
```

---

## 2. 前置基础

目前系统已完成：

```text
第 1 步：appId -> Arthas 命令执行网关。
第 2 步：diagnose_task 诊断任务管理和 arthas_command_record 命令审计。
第 3 步：固定规则诊断流程。
第 4 步：Spring AI 识别诊断类型，并根据 Arthas 输出生成报告。
第 5 步：Tool Calling。
第 6 步：SSE 实时输出。
```

本阶段新增：

```text
1. SQL 数据源管理。
2. SQL Explain 执行网关。
3. SQL 安全校验。
4. 表结构 / 索引 / 统计信息查询。
5. SQL 诊断记录。
6. SQL Explain Tool Calling。
7. Java + SQL 联合诊断报告。
```

---

## 3. 本阶段整体定位

本阶段不是让 AI 直接执行任意 SQL。

错误设计：

```text
用户问题 -> AI 生成 SQL -> 后端执行 SQL
```

正确设计：

```text
用户提供 SQL / 系统提取 SQL
  ↓
后端 SQL 安全校验
  ↓
只允许 EXPLAIN / 只读元数据查询
  ↓
执行 SQL Explain
  ↓
采集表结构、索引、统计信息
  ↓
AI 基于受控数据生成优化建议
```

核心原则：

```text
1. AI 不直接执行 SQL。
2. AI 不直接拼接 SQL。
3. 后端只执行受控 Explain 和元数据查询。
4. 禁止 INSERT / UPDATE / DELETE / DROP / TRUNCATE / ALTER / CREATE。
5. 禁止存储过程、函数调用、动态 SQL、批量语句。
6. 所有 SQL Explain 都必须落库审计。
7. 所有 SQL 诊断都归属到 taskNo。
```

---

## 4. Java + SQL 联合诊断流程

### 4.1 接口慢诊断增强流程

```text
用户输入：下单接口很慢，帮我定位
  ↓
AI 识别：SLOW_REQUEST
  ↓
Arthas 阶段：trace Controller / Service / Mapper
  ↓
如果耗时集中在 Mapper / DAO / JDBC
  ↓
SQL 阶段：获取 SQL、校验 SQL、执行 Explain、查询表结构/索引/统计信息
  ↓
AI 生成 Java + SQL 联合诊断报告
```

最终报告包含：

```text
1. Java 方法耗时链路
2. SQL 执行计划分析
3. 慢点归因
4. 索引建议
5. SQL 改写建议
6. Java 代码层优化建议
7. 风险提示
```

---

## 5. SQL 来源设计

### 5.1 方式一：用户手动提供 SQL

适合 MVP。

```json
{
  "taskNo": "DIAG-xxx",
  "datasourceCode": "order_db",
  "sql": "select * from t_order where user_id = ? and status = ?"
}
```

优点：

```text
1. 实现简单。
2. 不依赖日志系统。
3. 不依赖 MyBatis 拦截器。
4. 适合先跑通 Explain。
```

### 5.2 方式二：系统自动提取 SQL

后续增强。

来源可以包括：

```text
1. MyBatis SQL 日志。
2. P6Spy 日志。
3. 慢 SQL 日志。
4. APM 链路追踪。
5. 业务系统埋点。
6. trace 输出中识别 Mapper 方法后，映射到 Mapper XML。
```

建议：

```text
本阶段先做“用户手动提供 SQL”。
后续再做“自动提取 SQL”。
```

---

## 6. 支持的数据库范围

推荐支持顺序：

```text
1. MySQL
2. PostgreSQL / KingBase
3. Oracle
4. GBase
```

本阶段建议先支持 MySQL：

```text
MySQL:
EXPLAIN FORMAT=JSON select ...
```

后续扩展：

```text
PostgreSQL / KingBase:
EXPLAIN (FORMAT JSON, ANALYZE false, BUFFERS false) select ...

Oracle:
EXPLAIN PLAN FOR select ...
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY)
```

---

## 7. 数据库表设计

### 7.1 SQL 数据源配置表

```sql
CREATE TABLE sql_datasource_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    datasource_code VARCHAR(64) NOT NULL COMMENT '数据源编码，如 order_db',
    datasource_name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    db_type VARCHAR(32) NOT NULL COMMENT 'MYSQL/POSTGRESQL/ORACLE/KINGBASE/GBASE',
    jdbc_url VARCHAR(512) NOT NULL,
    username VARCHAR(128) NOT NULL,
    password_cipher VARCHAR(512) NOT NULL COMMENT '加密后的密码',
    env VARCHAR(32) NOT NULL COMMENT 'dev/test/prod',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_datasource_env (datasource_code, env)
);
```

安全建议：

```text
1. password_cipher 必须加密存储。
2. 不要在日志中打印 jdbc_url 完整敏感信息。
3. 生产环境建议使用只读账号。
4. 生产环境账号只允许 EXPLAIN 和元数据查询。
```

### 7.2 SQL 诊断任务表

```sql
CREATE TABLE sql_diagnosis_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL COMMENT '诊断任务编号',
    datasource_code VARCHAR(64) NOT NULL COMMENT '数据源编码',
    db_type VARCHAR(32) NOT NULL COMMENT '数据库类型',
    sql_hash VARCHAR(64) NOT NULL COMMENT 'SQL指纹/hash',
    original_sql MEDIUMTEXT NOT NULL COMMENT '原始SQL',
    normalized_sql MEDIUMTEXT DEFAULT NULL COMMENT '归一化SQL',
    explain_sql MEDIUMTEXT DEFAULT NULL COMMENT '实际执行的Explain SQL',
    explain_result MEDIUMTEXT DEFAULT NULL COMMENT 'Explain结果JSON或文本',
    table_meta_json MEDIUMTEXT DEFAULT NULL COMMENT '表结构信息',
    index_meta_json MEDIUMTEXT DEFAULT NULL COMMENT '索引信息',
    table_stats_json MEDIUMTEXT DEFAULT NULL COMMENT '表统计信息',
    diagnosis_result MEDIUMTEXT DEFAULT NULL COMMENT 'AI SQL诊断结果',
    status VARCHAR(32) NOT NULL COMMENT 'CREATED/RUNNING/FINISHED/FAILED',
    error_message TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_task_no (task_no),
    KEY idx_sql_hash (sql_hash),
    KEY idx_datasource_time (datasource_code, created_at)
);
```

### 7.3 SQL Explain 调用审计表

```sql
CREATE TABLE sql_tool_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    sql_record_id BIGINT DEFAULT NULL,
    datasource_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL COMMENT 'explainSql/getTableMeta/getIndexInfo/getTableStats',
    request_sql MEDIUMTEXT DEFAULT NULL,
    success TINYINT NOT NULL,
    cost_millis BIGINT NOT NULL,
    result_excerpt TEXT DEFAULT NULL,
    error_message TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_task_no (task_no),
    KEY idx_sql_record_id (sql_record_id)
);
```

### 7.4 可选：Mapper SQL 映射表

```sql
CREATE TABLE mapper_sql_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(32) NOT NULL,
    mapper_class VARCHAR(256) NOT NULL,
    mapper_method VARCHAR(128) NOT NULL,
    datasource_code VARCHAR(64) NOT NULL,
    sql_template MEDIUMTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_mapper_method (app_id, env, mapper_class, mapper_method)
);
```

MVP 可以不做。

---

## 8. 核心模块设计

新增模块建议：

```text
sql-diagnosis
├── datasource
│   ├── SqlDatasourceConfig
│   ├── SqlDatasourceService
│   └── DynamicDatasourceFactory
│
├── security
│   ├── SqlSafetyChecker
│   ├── SqlTypeDetector
│   └── SqlTableExtractor
│
├── explain
│   ├── SqlExplainExecutor
│   ├── MysqlExplainExecutor
│   ├── PostgreSqlExplainExecutor
│   ├── OracleExplainExecutor
│   └── ExplainExecutorFactory
│
├── metadata
│   ├── TableMetaService
│   ├── IndexMetaService
│   └── TableStatsService
│
├── tool
│   └── SqlDiagnosticTools
│
├── ai
│   ├── SqlDiagnosisReportGenerator
│   └── JavaSqlJointReportGenerator
│
└── controller
    ├── SqlDiagnosisController
    └── JointDiagnosisController
```

---

## 9. SQL 安全校验设计

### 9.1 只允许 SELECT / WITH SELECT

允许：

```text
SELECT ...
WITH ... SELECT ...
```

禁止：

```text
INSERT
UPDATE
DELETE
MERGE
DROP
TRUNCATE
ALTER
CREATE
REPLACE
GRANT
REVOKE
CALL
EXEC
EXECUTE
LOAD
INTO OUTFILE
LOCK
UNLOCK
ANALYZE
VACUUM
SET
SHOW PROCESSLIST
KILL
```

### 9.2 禁止多语句

禁止：

```text
select * from t_user; drop table t_user;
```

策略：

```text
1. 去除字符串字面量后检查分号数量。
2. 或者使用 SQL Parser。
3. MVP 可以先做简单校验，后续接 JSqlParser。
```

### 9.3 SqlSafetyChecker 示例

```java
package com.example.diagnosis.sql.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class SqlSafetyChecker {

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "insert", "update", "delete", "merge",
            "drop", "truncate", "alter", "create", "replace",
            "grant", "revoke", "call", "exec", "execute",
            "load", "outfile", "infile",
            "lock", "unlock", "kill"
    );

    public void checkExplainableSelect(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL不能为空");
        }

        String normalized = normalize(sql);

        if (hasMultiStatement(normalized)) {
            throw new SecurityException("禁止多语句SQL");
        }

        if (!isSelect(normalized)) {
            throw new SecurityException("只允许 SELECT 或 WITH SELECT SQL");
        }

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (containsKeyword(normalized, keyword)) {
                throw new SecurityException("SQL包含禁止关键字：" + keyword);
            }
        }
    }

    private String normalize(String sql) {
        return sql.trim()
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("--.*?(\\r?\\n|$)", " ")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isSelect(String sql) {
        return sql.startsWith("select ") || sql.startsWith("with ");
    }

    private boolean hasMultiStatement(String sql) {
        String trimmed = sql.trim();
        if (!trimmed.contains(";")) {
            return false;
        }
        return !trimmed.endsWith(";") || trimmed.indexOf(";") != trimmed.length() - 1;
    }

    private boolean containsKeyword(String sql, String keyword) {
        return sql.matches(".*\\b" + keyword + "\\b.*");
    }
}
```

生产建议：

```text
1. 使用 JSqlParser 做 AST 级别校验。
2. 使用只读数据库账号。
3. 使用数据库层权限兜底。
4. Explain 执行连接设置超时。
5. 限制 SQL 长度。
```

---

## 10. SQL Explain 执行器设计

### 10.1 SqlExplainExecutor 接口

```java
package com.example.diagnosis.sql.explain;

public interface SqlExplainExecutor {

    String dbType();

    SqlExplainResult explain(SqlExplainRequest request);
}
```

### 10.2 SqlExplainRequest

```java
package com.example.diagnosis.sql.explain;

import lombok.Data;

@Data
public class SqlExplainRequest {

    private String taskNo;

    private String datasourceCode;

    private String env;

    private String dbType;

    private String sql;
}
```

### 10.3 SqlExplainResult

```java
package com.example.diagnosis.sql.explain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SqlExplainResult {

    private String taskNo;

    private String datasourceCode;

    private String dbType;

    private String originalSql;

    private String explainSql;

    private String explainResult;

    private Boolean success;

    private String errorMessage;

    private Long costMillis;
}
```

### 10.4 MySQL Explain 实现

```java
package com.example.diagnosis.sql.explain;

import com.example.diagnosis.sql.datasource.DynamicDatasourceFactory;
import com.example.diagnosis.sql.security.SqlSafetyChecker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MysqlExplainExecutor implements SqlExplainExecutor {

    private final DynamicDatasourceFactory datasourceFactory;
    private final SqlSafetyChecker sqlSafetyChecker;

    public MysqlExplainExecutor(DynamicDatasourceFactory datasourceFactory,
                                SqlSafetyChecker sqlSafetyChecker) {
        this.datasourceFactory = datasourceFactory;
        this.sqlSafetyChecker = sqlSafetyChecker;
    }

    @Override
    public String dbType() {
        return "MYSQL";
    }

    @Override
    public SqlExplainResult explain(SqlExplainRequest request) {
        long start = System.currentTimeMillis();

        try {
            sqlSafetyChecker.checkExplainableSelect(request.getSql());

            JdbcTemplate jdbcTemplate = datasourceFactory.getJdbcTemplate(
                    request.getDatasourceCode(),
                    request.getEnv()
            );

            String explainSql = "EXPLAIN FORMAT=JSON " + removeEndSemicolon(request.getSql());

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(explainSql);
            String result = rows.toString();

            return SqlExplainResult.builder()
                    .taskNo(request.getTaskNo())
                    .datasourceCode(request.getDatasourceCode())
                    .dbType(dbType())
                    .originalSql(request.getSql())
                    .explainSql(explainSql)
                    .explainResult(result)
                    .success(true)
                    .costMillis(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return SqlExplainResult.builder()
                    .taskNo(request.getTaskNo())
                    .datasourceCode(request.getDatasourceCode())
                    .dbType(dbType())
                    .originalSql(request.getSql())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .costMillis(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private String removeEndSemicolon(String sql) {
        String text = sql.trim();
        if (text.endsWith(";")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }
}
```

---

## 11. 动态数据源设计

### 11.1 DynamicDatasourceFactory

```java
package com.example.diagnosis.sql.datasource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicDatasourceFactory {

    private final SqlDatasourceService datasourceService;

    private final ConcurrentHashMap<String, JdbcTemplate> cache = new ConcurrentHashMap<>();

    public DynamicDatasourceFactory(SqlDatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    public JdbcTemplate getJdbcTemplate(String datasourceCode, String env) {
        String key = datasourceCode + ":" + env;

        return cache.computeIfAbsent(key, k -> {
            DataSource dataSource = datasourceService.createDataSource(datasourceCode, env);
            return new JdbcTemplate(dataSource);
        });
    }
}
```

### 11.2 SqlDatasourceService

```java
package com.example.diagnosis.sql.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class SqlDatasourceService {

    private final SqlDatasourceConfigMapper datasourceConfigMapper;
    private final PasswordCipherService passwordCipherService;

    public SqlDatasourceService(SqlDatasourceConfigMapper datasourceConfigMapper,
                                PasswordCipherService passwordCipherService) {
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.passwordCipherService = passwordCipherService;
    }

    public DataSource createDataSource(String datasourceCode, String env) {
        SqlDatasourceConfig config = datasourceConfigMapper.findEnabled(datasourceCode, env);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在或未启用：" + datasourceCode + ", env=" + env);
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(passwordCipherService.decrypt(config.getPasswordCipher()));
        hikari.setMaximumPoolSize(3);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(3000);
        hikari.setValidationTimeout(2000);
        hikari.setReadOnly(true);

        return new HikariDataSource(hikari);
    }
}
```

建议：

```text
1. Explain 数据源使用独立只读账号。
2. 每个数据源连接池最大连接数要小。
3. 不要复用业务系统写库账号。
```

---

## 12. 表结构、索引、统计信息查询

### 12.1 TableMetaService

```java
package com.example.diagnosis.sql.metadata;

import com.example.diagnosis.sql.datasource.DynamicDatasourceFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TableMetaService {

    private final DynamicDatasourceFactory datasourceFactory;

    public TableMetaService(DynamicDatasourceFactory datasourceFactory) {
        this.datasourceFactory = datasourceFactory;
    }

    public List<Map<String, Object>> getMysqlColumns(String datasourceCode, String env, String tableName) {
        JdbcTemplate jdbcTemplate = datasourceFactory.getJdbcTemplate(datasourceCode, env);

        return jdbcTemplate.queryForList("""
                SELECT
                    TABLE_SCHEMA,
                    TABLE_NAME,
                    COLUMN_NAME,
                    COLUMN_TYPE,
                    IS_NULLABLE,
                    COLUMN_KEY,
                    COLUMN_DEFAULT,
                    EXTRA,
                    COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """, tableName);
    }
}
```

### 12.2 IndexMetaService

```java
package com.example.diagnosis.sql.metadata;

import com.example.diagnosis.sql.datasource.DynamicDatasourceFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class IndexMetaService {

    private final DynamicDatasourceFactory datasourceFactory;

    public IndexMetaService(DynamicDatasourceFactory datasourceFactory) {
        this.datasourceFactory = datasourceFactory;
    }

    public List<Map<String, Object>> getMysqlIndexes(String datasourceCode, String env, String tableName) {
        JdbcTemplate jdbcTemplate = datasourceFactory.getJdbcTemplate(datasourceCode, env);

        return jdbcTemplate.queryForList("""
                SELECT
                    TABLE_SCHEMA,
                    TABLE_NAME,
                    INDEX_NAME,
                    NON_UNIQUE,
                    SEQ_IN_INDEX,
                    COLUMN_NAME,
                    CARDINALITY,
                    INDEX_TYPE
                FROM information_schema.STATISTICS
                WHERE TABLE_NAME = ?
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """, tableName);
    }
}
```

### 12.3 TableStatsService

```java
package com.example.diagnosis.sql.metadata;

import com.example.diagnosis.sql.datasource.DynamicDatasourceFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TableStatsService {

    private final DynamicDatasourceFactory datasourceFactory;

    public TableStatsService(DynamicDatasourceFactory datasourceFactory) {
        this.datasourceFactory = datasourceFactory;
    }

    public List<Map<String, Object>> getMysqlTableStats(String datasourceCode, String env, String tableName) {
        JdbcTemplate jdbcTemplate = datasourceFactory.getJdbcTemplate(datasourceCode, env);

        return jdbcTemplate.queryForList("""
                SELECT
                    TABLE_SCHEMA,
                    TABLE_NAME,
                    TABLE_ROWS,
                    DATA_LENGTH,
                    INDEX_LENGTH,
                    UPDATE_TIME,
                    TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_NAME = ?
                """, tableName);
    }
}
```

---

## 13. SQL Diagnostic Tools

如果已经接入 Spring AI Tool Calling，可以把 SQL Explain 能力暴露为 Tool。

### 13.1 Tool 列表

```text
explainSql(taskNo, datasourceCode, env, sql)
getTableMeta(taskNo, datasourceCode, env, tableName)
getIndexInfo(taskNo, datasourceCode, env, tableName)
getTableStats(taskNo, datasourceCode, env, tableName)
```

### 13.2 SqlDiagnosticTools

```java
package com.example.diagnosis.sql.tool;

import com.example.diagnosis.sql.explain.*;
import com.example.diagnosis.sql.metadata.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SqlDiagnosticTools {

    private final ExplainExecutorFactory explainExecutorFactory;
    private final TableMetaService tableMetaService;
    private final IndexMetaService indexMetaService;
    private final TableStatsService tableStatsService;

    public SqlDiagnosticTools(ExplainExecutorFactory explainExecutorFactory,
                              TableMetaService tableMetaService,
                              IndexMetaService indexMetaService,
                              TableStatsService tableStatsService) {
        this.explainExecutorFactory = explainExecutorFactory;
        this.tableMetaService = tableMetaService;
        this.indexMetaService = indexMetaService;
        this.tableStatsService = tableStatsService;
    }

    @Tool(description = "对只读 SELECT SQL 执行 Explain，获取 SQL 执行计划。禁止执行 DML/DDL。")
    public SqlExplainResult explainSql(String taskNo,
                                       String datasourceCode,
                                       String env,
                                       String dbType,
                                       String sql) {
        SqlExplainRequest request = new SqlExplainRequest();
        request.setTaskNo(taskNo);
        request.setDatasourceCode(datasourceCode);
        request.setEnv(env);
        request.setDbType(dbType);
        request.setSql(sql);

        SqlExplainExecutor executor = explainExecutorFactory.getExecutor(dbType);
        return executor.explain(request);
    }

    @Tool(description = "查询指定表的字段结构信息")
    public List<Map<String, Object>> getTableMeta(String taskNo,
                                                  String datasourceCode,
                                                  String env,
                                                  String tableName) {
        return tableMetaService.getMysqlColumns(datasourceCode, env, tableName);
    }

    @Tool(description = "查询指定表的索引信息")
    public List<Map<String, Object>> getIndexInfo(String taskNo,
                                                  String datasourceCode,
                                                  String env,
                                                  String tableName) {
        return indexMetaService.getMysqlIndexes(datasourceCode, env, tableName);
    }

    @Tool(description = "查询指定表的统计信息，例如行数、数据大小、索引大小")
    public List<Map<String, Object>> getTableStats(String taskNo,
                                                   String datasourceCode,
                                                   String env,
                                                   String tableName) {
        return tableStatsService.getMysqlTableStats(datasourceCode, env, tableName);
    }
}
```

注意：

```text
Tool 方法内部还需要增加：
1. taskNo 校验。
2. datasource 权限校验。
3. tableName 合法性校验。
4. 调用次数限制。
5. 审计日志记录。
```

---

## 14. SQL 诊断接口设计

### 14.1 SQL Explain 请求

```java
package com.example.diagnosis.sql.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SqlExplainApiRequest {

    @NotBlank(message = "taskNo不能为空")
    private String taskNo;

    @NotBlank(message = "datasourceCode不能为空")
    private String datasourceCode;

    @NotBlank(message = "env不能为空")
    private String env;

    @NotBlank(message = "dbType不能为空")
    private String dbType;

    @NotBlank(message = "sql不能为空")
    private String sql;
}
```

### 14.2 SqlDiagnosisController

```java
package com.example.diagnosis.sql.controller;

import com.example.diagnosis.sql.domain.SqlExplainApiRequest;
import com.example.diagnosis.sql.explain.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sql/diagnose")
public class SqlDiagnosisController {

    private final ExplainExecutorFactory explainExecutorFactory;

    public SqlDiagnosisController(ExplainExecutorFactory explainExecutorFactory) {
        this.explainExecutorFactory = explainExecutorFactory;
    }

    @PostMapping("/explain")
    public SqlExplainResult explain(@Valid @RequestBody SqlExplainApiRequest request) {
        SqlExplainRequest explainRequest = new SqlExplainRequest();
        explainRequest.setTaskNo(request.getTaskNo());
        explainRequest.setDatasourceCode(request.getDatasourceCode());
        explainRequest.setEnv(request.getEnv());
        explainRequest.setDbType(request.getDbType());
        explainRequest.setSql(request.getSql());

        SqlExplainExecutor executor = explainExecutorFactory.getExecutor(request.getDbType());
        return executor.explain(explainRequest);
    }
}
```

### 14.3 ExplainExecutorFactory

```java
package com.example.diagnosis.sql.explain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExplainExecutorFactory {

    private final Map<String, SqlExplainExecutor> executorMap;

    public ExplainExecutorFactory(List<SqlExplainExecutor> executors) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(SqlExplainExecutor::dbType, e -> e));
    }

    public SqlExplainExecutor getExecutor(String dbType) {
        SqlExplainExecutor executor = executorMap.get(dbType);
        if (executor == null) {
            throw new IllegalArgumentException("暂不支持该数据库类型：" + dbType);
        }
        return executor;
    }
}
```

---

## 15. Java + SQL 联合诊断报告生成

### 15.1 联合报告上下文

报告上下文包含：

```text
1. diagnose_task 任务信息。
2. Arthas trace 输出。
3. SQL 原文。
4. SQL Explain 输出。
5. 表结构信息。
6. 索引信息。
7. 表统计信息。
8. SQL Tool 调用记录。
```

### 15.2 联合诊断 Prompt

```text
你是一个资深 Java 后端性能诊断专家，熟悉 Arthas、JVM、MyBatis、SQL 执行计划、索引优化和数据库性能分析。

你需要基于以下信息生成 Java + SQL 联合诊断报告：

1. 用户问题
2. Arthas trace 输出
3. SQL 原文
4. SQL Explain 结果
5. 表结构
6. 索引信息
7. 表统计信息

必须遵守：
1. 只能基于提供的信息分析，不允许编造。
2. 如果证据不足，必须明确说明需要补充哪些信息。
3. 不允许建议直接在线上执行高风险 SQL。
4. 索引建议必须说明适用条件和风险。
5. SQL 改写建议必须说明可能影响。
6. 如果需要进一步验证，应建议在测试环境或低峰期执行。
7. 不允许建议删除数据、重建表、直接修改生产结构，除非明确说明需要评审和变更流程。

报告结构：

# Java + SQL 联合诊断报告

## 1. 问题现象
## 2. Java 方法耗时分析
## 3. SQL 执行计划分析
## 4. 关键瓶颈判断
## 5. 索引优化建议
## 6. SQL 改写建议
## 7. Java 代码层优化建议
## 8. 风险提示
## 9. 后续验证方案
## 10. 结论摘要
```

### 15.3 JavaSqlJointReportGenerator

```java
package com.example.diagnosis.sql.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class JavaSqlJointReportGenerator {

    private final ChatClient chatClient;

    public JavaSqlJointReportGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generate(JavaSqlJointReportContext context) {
        return chatClient.prompt()
                .system("""
                        你是一个资深 Java 后端性能诊断专家，熟悉 Arthas、JVM、MyBatis、SQL 执行计划、索引优化和数据库性能分析。
                        你必须基于提供的数据生成诊断报告，不允许编造。
                        索引建议必须说明适用条件和风险。
                        SQL 改写建议必须说明可能影响。
                        不允许建议直接在线上执行高风险 SQL。
                        """)
                .user("""
                        用户问题：
                        %s

                        Arthas 输出：
                        %s

                        SQL 原文：
                        %s

                        SQL Explain 结果：
                        %s

                        表结构：
                        %s

                        索引信息：
                        %s

                        表统计信息：
                        %s

                        请生成 Markdown 格式的 Java + SQL 联合诊断报告。
                        """.formatted(
                        context.getQuestion(),
                        context.getArthasOutput(),
                        context.getSql(),
                        context.getExplainResult(),
                        context.getTableMeta(),
                        context.getIndexMeta(),
                        context.getTableStats()
                ))
                .call()
                .content();
    }
}
```

---

## 16. 联合诊断编排流程

### 16.1 JavaSqlJointDiagnosisService

```java
package com.example.diagnosis.sql.service;

import com.example.diagnosis.domain.ArthasCommandRecord;
import com.example.diagnosis.domain.DiagnoseTask;
import com.example.diagnosis.mapper.ArthasCommandRecordMapper;
import com.example.diagnosis.service.DiagnoseTaskService;
import com.example.diagnosis.sql.ai.JavaSqlJointReportGenerator;
import com.example.diagnosis.sql.explain.*;
import com.example.diagnosis.sql.metadata.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class JavaSqlJointDiagnosisService {

    private final DiagnoseTaskService diagnoseTaskService;
    private final ArthasCommandRecordMapper arthasCommandRecordMapper;
    private final ExplainExecutorFactory explainExecutorFactory;
    private final TableMetaService tableMetaService;
    private final IndexMetaService indexMetaService;
    private final TableStatsService tableStatsService;
    private final JavaSqlJointReportGenerator reportGenerator;

    public JavaSqlJointDiagnosisService(DiagnoseTaskService diagnoseTaskService,
                                        ArthasCommandRecordMapper arthasCommandRecordMapper,
                                        ExplainExecutorFactory explainExecutorFactory,
                                        TableMetaService tableMetaService,
                                        IndexMetaService indexMetaService,
                                        TableStatsService tableStatsService,
                                        JavaSqlJointReportGenerator reportGenerator) {
        this.diagnoseTaskService = diagnoseTaskService;
        this.arthasCommandRecordMapper = arthasCommandRecordMapper;
        this.explainExecutorFactory = explainExecutorFactory;
        this.tableMetaService = tableMetaService;
        this.indexMetaService = indexMetaService;
        this.tableStatsService = tableStatsService;
        this.reportGenerator = reportGenerator;
    }

    public String diagnose(JavaSqlJointDiagnosisRequest request) {
        DiagnoseTask task = diagnoseTaskService.getByTaskNo(request.getTaskNo());
        List<ArthasCommandRecord> arthasRecords = arthasCommandRecordMapper.findByTaskNo(request.getTaskNo());

        SqlExplainRequest explainRequest = new SqlExplainRequest();
        explainRequest.setTaskNo(request.getTaskNo());
        explainRequest.setDatasourceCode(request.getDatasourceCode());
        explainRequest.setEnv(request.getEnv());
        explainRequest.setDbType(request.getDbType());
        explainRequest.setSql(request.getSql());

        SqlExplainExecutor executor = explainExecutorFactory.getExecutor(request.getDbType());
        SqlExplainResult explainResult = executor.explain(explainRequest);

        if (!Boolean.TRUE.equals(explainResult.getSuccess())) {
            throw new RuntimeException("SQL Explain执行失败：" + explainResult.getErrorMessage());
        }

        String tableName = request.getMainTableName();

        List<Map<String, Object>> tableMeta = tableMetaService.getMysqlColumns(
                request.getDatasourceCode(), request.getEnv(), tableName);

        List<Map<String, Object>> indexMeta = indexMetaService.getMysqlIndexes(
                request.getDatasourceCode(), request.getEnv(), tableName);

        List<Map<String, Object>> tableStats = tableStatsService.getMysqlTableStats(
                request.getDatasourceCode(), request.getEnv(), tableName);

        JavaSqlJointReportContext context = new JavaSqlJointReportContext();
        context.setQuestion(task.getQuestion());
        context.setArthasOutput(arthasRecords.toString());
        context.setSql(request.getSql());
        context.setExplainResult(explainResult.getExplainResult());
        context.setTableMeta(tableMeta.toString());
        context.setIndexMeta(indexMeta.toString());
        context.setTableStats(tableStats.toString());

        return reportGenerator.generate(context);
    }
}
```

---

## 17. 联合诊断接口设计

### 17.1 JavaSqlJointDiagnosisRequest

```java
package com.example.diagnosis.sql.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JavaSqlJointDiagnosisRequest {

    @NotBlank(message = "taskNo不能为空")
    private String taskNo;

    @NotBlank(message = "datasourceCode不能为空")
    private String datasourceCode;

    @NotBlank(message = "env不能为空")
    private String env;

    @NotBlank(message = "dbType不能为空")
    private String dbType;

    @NotBlank(message = "sql不能为空")
    private String sql;

    /**
     * MVP 先让用户传主表。
     * 后续可以通过 SQL Parser 自动提取。
     */
    @NotBlank(message = "mainTableName不能为空")
    private String mainTableName;
}
```

### 17.2 JavaSqlJointDiagnosisController

```java
package com.example.diagnosis.sql.controller;

import com.example.diagnosis.sql.domain.JavaSqlJointDiagnosisRequest;
import com.example.diagnosis.sql.service.JavaSqlJointDiagnosisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/joint-diagnose")
public class JavaSqlJointDiagnosisController {

    private final JavaSqlJointDiagnosisService jointDiagnosisService;

    public JavaSqlJointDiagnosisController(JavaSqlJointDiagnosisService jointDiagnosisService) {
        this.jointDiagnosisService = jointDiagnosisService;
    }

    @PostMapping("/java-sql")
    public String diagnose(@Valid @RequestBody JavaSqlJointDiagnosisRequest request) {
        return jointDiagnosisService.diagnose(request);
    }
}
```

---

## 18. SSE 事件扩展

第 6 步已经完成 SSE，本阶段新增 SQL 事件类型。

```java
public enum DiagnoseEventType {

    TASK_CREATED,
    INTENT_CLASSIFYING,
    INTENT_CLASSIFIED,
    PLAN_CREATED,
    TOOL_CALL_START,
    TOOL_CALL_SUCCESS,
    TOOL_CALL_FAILED,

    SQL_EXPLAIN_START,
    SQL_EXPLAIN_SUCCESS,
    SQL_EXPLAIN_FAILED,
    SQL_META_COLLECTING,
    SQL_META_COLLECTED,
    JOINT_REPORT_GENERATING,
    JOINT_REPORT_GENERATED,

    AI_ANALYZING,
    REPORT_GENERATED,
    TASK_FINISHED,
    TASK_FAILED,
    HEARTBEAT
}
```

执行 SQL Explain 时推送：

```text
SQL_EXPLAIN_START
SQL_EXPLAIN_SUCCESS / SQL_EXPLAIN_FAILED
SQL_META_COLLECTING
SQL_META_COLLECTED
JOINT_REPORT_GENERATING
JOINT_REPORT_GENERATED
```

---

## 19. Tool Calling 扩展建议

可以把 SQL 工具暴露给 Agent：

```text
explainSql
getTableMeta
getIndexInfo
getTableStats
```

但建议先由后端规则控制 SQL Explain 流程，不要一开始完全交给 AI 自主调用。

推荐模式：

```text
接口慢诊断
  ↓
Arthas trace 完成
  ↓
用户确认 SQL 或系统识别 SQL
  ↓
后端规则执行 explainSql + metadata
  ↓
AI 生成联合报告
```

后续再升级为：

```text
AI 根据 trace 输出判断是否需要调用 explainSql Tool。
```

---

## 20. 前端交互设计

### 20.1 接口慢诊断完成后提示

如果 Arthas trace 结果显示 Mapper / DAO 层耗时较高，前端展示：

```text
检测到接口慢可能与 SQL 执行有关，是否进入 SQL Explain 诊断？
```

用户填写：

```text
1. 数据源 datasourceCode
2. 数据库类型 dbType
3. SQL 原文
4. 主表 mainTableName
```

然后调用：

```http
POST /api/joint-diagnose/java-sql
```

### 20.2 展示联合诊断报告

报告页面分为：

```text
1. Java 诊断概览
2. Arthas trace 耗时链路
3. SQL Explain 执行计划
4. 表结构
5. 索引信息
6. 优化建议
7. 风险提示
```

---

## 21. 安全控制重点

### 21.1 数据源安全

```text
1. 使用只读账号。
2. 生产环境数据源需要权限审批。
3. 数据库密码加密存储。
4. 不允许前端直接传 JDBC URL。
5. 只能选择系统配置好的 datasourceCode。
```

### 21.2 SQL 安全

```text
1. 只允许 SELECT / WITH SELECT。
2. 禁止多语句。
3. 禁止 DML / DDL / DCL。
4. 禁止存储过程和函数调用。
5. SQL 长度限制。
6. Explain 超时限制。
7. SQL 原文脱敏后再进入 AI。
```

### 21.3 AI 安全

```text
1. AI 不执行 SQL。
2. AI 不生成可执行 SQL 后直接执行。
3. AI 只基于 Explain 和元数据生成建议。
4. AI 生成索引建议必须提示评审和测试验证。
5. AI 不允许建议直接在线上变更表结构。
```

---

## 22. 验收标准

完成以下内容，即认为 Java + SQL 联合诊断阶段完成：

```text
1. 新增 sql_datasource_config 表。
2. 新增 sql_diagnosis_record 表。
3. 新增 sql_tool_call_record 表。
4. 支持配置 SQL 诊断数据源。
5. 支持通过 datasourceCode 获取 JdbcTemplate。
6. 支持 SQL 安全校验，只允许 SELECT / WITH SELECT。
7. 禁止 DML / DDL / 多语句 SQL。
8. 支持 MySQL EXPLAIN FORMAT=JSON。
9. 支持查询表字段信息。
10. 支持查询索引信息。
11. 支持查询表统计信息。
12. 支持 /api/sql/diagnose/explain 接口。
13. 支持 /api/joint-diagnose/java-sql 联合诊断接口。
14. SQL Explain 调用有审计记录。
15. SQL 诊断归属到 taskNo。
16. 能基于 Arthas trace + SQL Explain 生成联合诊断报告。
17. SSE 能推送 SQL Explain 相关事件。
18. SQL Explain 失败时能明确返回错误原因。
```

---

## 23. 建议开发顺序

```text
1. 新增 sql_datasource_config 表。
2. 新增 sql_diagnosis_record 表。
3. 新增 sql_tool_call_record 表。
4. 实现 SqlDatasourceService 和 DynamicDatasourceFactory。
5. 实现 SqlSafetyChecker。
6. 实现 SqlExplainExecutor 接口。
7. 实现 MysqlExplainExecutor。
8. 实现 ExplainExecutorFactory。
9. 实现 TableMetaService。
10. 实现 IndexMetaService。
11. 实现 TableStatsService。
12. 实现 SqlDiagnosisController。
13. 实现 SqlDiagnosticTools。
14. 实现 JavaSqlJointReportGenerator。
15. 实现 JavaSqlJointDiagnosisService。
16. 实现 JavaSqlJointDiagnosisController。
17. 扩展 SSE 事件类型。
18. 测试慢接口 trace + SQL Explain 联合报告。
```

---

## 24. 推荐测试用例

### 用例 1：正常 SELECT Explain

```json
{
  "taskNo": "DIAG-xxx",
  "datasourceCode": "order_db",
  "env": "test",
  "dbType": "MYSQL",
  "sql": "select * from t_order where user_id = 1001",
  "mainTableName": "t_order"
}
```

预期：

```text
Explain 成功。
返回执行计划。
生成联合诊断报告。
```

### 用例 2：禁止 UPDATE

```json
{
  "sql": "update t_order set status = 'X' where id = 1"
}
```

预期：

```text
SQL 安全校验失败。
不执行数据库调用。
记录失败原因。
```

### 用例 3：禁止多语句

```json
{
  "sql": "select * from t_order; drop table t_order;"
}
```

预期：

```text
拒绝执行。
提示禁止多语句 SQL。
```

### 用例 4：缺少主表

```json
{
  "sql": "select * from t_order where user_id = 1001",
  "mainTableName": ""
}
```

预期：

```text
参数校验失败。
提示 mainTableName 不能为空。
```

### 用例 5：Explain 执行失败

场景：

```text
SQL 语法错误。
数据源不可用。
数据库连接超时。
```

预期：

```text
返回明确错误。
sql_diagnosis_record 标记 FAILED。
SSE 推送 SQL_EXPLAIN_FAILED。
```

---

## 25. 当前阶段不做的事情

```text
1. 不自动修改索引。
2. 不自动执行 ALTER TABLE。
3. 不自动执行 ANALYZE TABLE。
4. 不自动执行 SQL 改写后的 SQL。
5. 不自动从日志系统提取 SQL。
6. 不自动解析复杂 SQL 中所有表。
7. 不做真实执行耗时对比。
```

这些可以作为后续阶段：

```text
1. 接入慢 SQL 日志。
2. 接入 MyBatis SQL 捕获。
3. 接入 SQL Parser 自动提取表名。
4. 接入索引推荐评估。
5. 接入优化前后 Explain 对比。
6. 接入 RAG 数据库优化知识库。
```

---

## 26. 总结

接入 SQL Explain 后，系统从：

```text
Java 运行时诊断 Agent
```

升级为：

```text
Java + SQL 性能联合诊断 Agent
```

核心闭环变为：

```text
接口慢
  ↓
Arthas trace 定位 Java 方法耗时
  ↓
发现 Mapper / DAO / JDBC 层耗时
  ↓
SQL Explain 分析执行计划
  ↓
查询表结构 / 索引 / 统计信息
  ↓
AI 生成联合诊断报告
```

最终价值：

```text
1. 不只告诉用户“接口慢”。
2. 能进一步定位“慢在 Java 逻辑、远程调用、还是 SQL 查询”。
3. 如果慢在 SQL，能结合 Explain 和索引信息给出可落地优化建议。
4. 形成 Java 后端面试中非常有区分度的 AI Agent 项目。
```
