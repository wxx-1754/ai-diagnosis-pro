CREATE TABLE diagnose_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    message VARCHAR(1024) DEFAULT NULL,
    command VARCHAR(512) DEFAULT NULL,
    tool_name VARCHAR(128) DEFAULT NULL,
    success TINYINT DEFAULT NULL,
    data_json MEDIUMTEXT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_diagnose_event_task_id (task_no, id),
    KEY idx_diagnose_event_task_time (task_no, created_at)
);
