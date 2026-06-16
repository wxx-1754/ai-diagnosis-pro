ALTER TABLE diagnose_task
    ADD COLUMN target_uri VARCHAR(256) DEFAULT NULL COMMENT '接口慢诊断目标URI' AFTER diagnose_type,
    ADD COLUMN target_class VARCHAR(256) DEFAULT NULL COMMENT '接口慢诊断目标类名' AFTER target_uri,
    ADD COLUMN target_method VARCHAR(128) DEFAULT NULL COMMENT '接口慢诊断目标方法名' AFTER target_class;
