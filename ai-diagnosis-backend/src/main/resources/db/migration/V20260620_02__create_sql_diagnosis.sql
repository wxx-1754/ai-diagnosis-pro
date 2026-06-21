CREATE TABLE IF NOT EXISTS sql_datasource_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    datasource_code VARCHAR(64) NOT NULL COMMENT '数据源编码',
    datasource_name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    db_type VARCHAR(32) NOT NULL COMMENT '当前仅支持 MYSQL',
    jdbc_url VARCHAR(512) NOT NULL,
    username VARCHAR(128) NOT NULL,
    password_cipher VARCHAR(1024) NOT NULL COMMENT 'AES-GCM 加密后的密码',
    env VARCHAR(32) NOT NULL COMMENT 'dev/test/prod',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_sql_datasource_env (datasource_code, env),
    KEY idx_sql_datasource_status (env, status)
);

CREATE TABLE IF NOT EXISTS sql_diagnosis_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    datasource_code VARCHAR(64) NOT NULL,
    db_type VARCHAR(32) NOT NULL,
    main_table_name VARCHAR(128) NOT NULL,
    sql_hash VARCHAR(64) NOT NULL,
    original_sql MEDIUMTEXT NOT NULL,
    normalized_sql MEDIUMTEXT DEFAULT NULL,
    explain_sql MEDIUMTEXT DEFAULT NULL,
    explain_result MEDIUMTEXT DEFAULT NULL,
    table_meta_json MEDIUMTEXT DEFAULT NULL,
    index_meta_json MEDIUMTEXT DEFAULT NULL,
    table_stats_json MEDIUMTEXT DEFAULT NULL,
    diagnosis_result MEDIUMTEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'CREATED/RUNNING/FINISHED/FAILED',
    active_task_no VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status IN ('CREATED', 'RUNNING') THEN task_no ELSE NULL END
    ) STORED,
    error_message TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_sql_diagnosis_task (task_no, id),
    KEY idx_sql_diagnosis_hash (sql_hash),
    KEY idx_sql_diagnosis_datasource_time (datasource_code, created_at),
    KEY idx_sql_diagnosis_status (task_no, status),
    UNIQUE KEY uk_sql_diagnosis_active_task (active_task_no)
);

CREATE TABLE IF NOT EXISTS sql_tool_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    sql_record_id BIGINT DEFAULT NULL,
    datasource_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    request_sql MEDIUMTEXT DEFAULT NULL,
    success TINYINT NOT NULL,
    cost_millis BIGINT NOT NULL,
    result_excerpt TEXT DEFAULT NULL,
    error_message TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_sql_tool_task (task_no),
    KEY idx_sql_tool_record (sql_record_id)
);
