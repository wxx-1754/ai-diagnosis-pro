CREATE TABLE IF NOT EXISTS diagnose_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL COMMENT '诊断任务编号',
    report_title VARCHAR(256) DEFAULT NULL COMMENT '报告标题',
    report_markdown MEDIUMTEXT NOT NULL COMMENT 'Markdown格式诊断报告',
    report_json MEDIUMTEXT DEFAULT NULL COMMENT '结构化报告JSON',
    ai_model VARCHAR(128) DEFAULT NULL COMMENT '使用的模型',
    prompt_version VARCHAR(64) DEFAULT NULL COMMENT 'Prompt版本',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_task_no (task_no)
);
