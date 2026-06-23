-- app_instance 表在历史版本中由人工 DDL 创建（见 docs/design.md），未纳入 Flyway 管理。
-- 这里用 CREATE TABLE IF NOT EXISTS 幂等纳入：已存在的库不会重建，空库可由 Flyway 直接建出。
CREATE TABLE IF NOT EXISTS app_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL COMMENT '应用标识',
    app_name VARCHAR(128) NOT NULL COMMENT '应用名称',
    env VARCHAR(32) NOT NULL COMMENT 'dev/test/prod 等环境标识',
    ip VARCHAR(64) NOT NULL COMMENT '目标实例 IP',
    arthas_http_port INT NOT NULL COMMENT 'Arthas HTTP 端口',
    arthas_username VARCHAR(64) DEFAULT NULL COMMENT 'Arthas HTTP Basic 认证用户名',
    arthas_password VARCHAR(128) DEFAULT NULL COMMENT '已废弃：明文密码，保留只为兼容存量数据，新写入改用 password_cipher',
    arthas_agent_id VARCHAR(128) DEFAULT NULL COMMENT 'Arthas Agent ID（TUNNEL 模式预留）',
    access_mode VARCHAR(32) NOT NULL DEFAULT 'HTTP' COMMENT 'HTTP / TUNNEL',
    status VARCHAR(32) NOT NULL DEFAULT 'ONLINE' COMMENT 'ONLINE / OFFLINE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_app_env (app_id, env),
    KEY idx_app_instance_status (status)
);
