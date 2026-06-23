ALTER TABLE app_instance
    MODIFY COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT 'HTTP 模式目标实例 IP；TUNNEL 模式为空',
    MODIFY COLUMN arthas_http_port INT DEFAULT NULL COMMENT 'HTTP 模式 Arthas 端口；TUNNEL 模式为空';

CREATE UNIQUE INDEX uk_app_instance_agent_id ON app_instance (arthas_agent_id);
