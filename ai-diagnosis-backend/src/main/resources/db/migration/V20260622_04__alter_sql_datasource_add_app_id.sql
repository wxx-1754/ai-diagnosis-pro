-- SQL 数据源与服务实例强绑定：每个数据源必须归属一个 app（app_instance.app_id）。
-- 诊断某 app 时只匹配该 app 的专属数据源，杜绝跨应用选错库。
--
-- 存量数据源无 app 归属，NOT NULL 约束会阻断迁移，故用 DEFAULT '__LEGACY__' 兜底：
-- 遗留数据源临时挂到虚拟 app '__LEGACY__' 下，因「仅专属、无回退」语义不会被任何真实 app 选中，
-- 管理员需在后台逐个编辑重新绑定到真实 appId（或删除）。
ALTER TABLE sql_datasource_config
    ADD COLUMN app_id VARCHAR(64) NOT NULL DEFAULT '__LEGACY__' COMMENT '归属应用 appId（强绑定 app_instance）' AFTER datasource_name;

-- 唯一键从 (datasource_code, env) 调整为 (app_id, datasource_code, env)：
-- 允许不同 app 各自配置同名 datasource_code（多 app 共享同一物理库时各自维护一行）。
ALTER TABLE sql_datasource_config
    DROP INDEX uk_sql_datasource_env,
    ADD UNIQUE KEY uk_sql_datasource_app_env (app_id, datasource_code, env);

-- 按应用+环境检索启用数据源的主索引。
ALTER TABLE sql_datasource_config
    ADD KEY idx_sql_datasource_app_env (app_id, env, status);
