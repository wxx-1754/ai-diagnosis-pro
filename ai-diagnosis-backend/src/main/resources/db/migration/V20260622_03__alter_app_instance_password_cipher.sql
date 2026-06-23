-- Arthas 密码加密改造：新增 password_cipher 列存储 AES-GCM 密文（v1: 前缀 + Base64）。
-- arthas_password 旧明文列保留，仅为兼容存量数据；新写入只写 password_cipher。
-- 存量明文不做 SQL 层迁移，由应用层在读取时按「password_cipher 优先，否则回退 arthas_password」兼容，
-- 管理员可在后台逐个编辑实例触发重新加密落库。
ALTER TABLE app_instance
    ADD COLUMN password_cipher VARCHAR(1024) DEFAULT NULL COMMENT 'AES-GCM 加密后的 Arthas 密码' AFTER arthas_password;
