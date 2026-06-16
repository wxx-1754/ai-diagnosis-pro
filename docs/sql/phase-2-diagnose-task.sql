CREATE TABLE IF NOT EXISTS diagnose_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL COMMENT '诊断任务编号',
    app_id VARCHAR(64) NOT NULL COMMENT '应用ID',
    env VARCHAR(32) NOT NULL COMMENT '环境：dev/test/prod',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '操作用户',
    question TEXT DEFAULT NULL COMMENT '用户问题',
    diagnose_type VARCHAR(64) DEFAULT NULL COMMENT '诊断类型：HIGH_CPU/SLOW_REQUEST/THREAD_BLOCKED/MEMORY_ABNORMAL/UNKNOWN',
    status VARCHAR(32) NOT NULL COMMENT '任务状态：CREATED/RUNNING/FINISHED/FAILED',
    conclusion TEXT DEFAULT NULL COMMENT '诊断结论摘要，后续报告生成阶段使用',
    error_message TEXT DEFAULT NULL COMMENT '失败原因',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_app_env_time (app_id, env, created_at),
    KEY idx_status_time (status, created_at)
);

ALTER TABLE arthas_command_record
    ADD COLUMN task_no VARCHAR(64) DEFAULT NULL COMMENT '诊断任务编号' AFTER request_no;

CREATE INDEX idx_task_no ON arthas_command_record(task_no);
