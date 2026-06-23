package com.wuxx.diagnosis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App Instance 管理面配置。
 * 密码加密复用 SQL 诊断模块的 PasswordCipherService（AES-GCM，密钥来自 DIAGNOSIS_SQL_ENCRYPTION_KEY），
 * 因此这里不再单独配置加密密钥，避免多套密钥带来的运维负担。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "diagnosis.app-instance")
public class DiagnosisAppInstanceProperties {

    private boolean adminEnabled = false;

    private String adminToken;

    /** 连通性测试连接超时（毫秒）。 */
    private int connectTimeoutMs = 3000;

    /** 连通性测试读取超时（毫秒）。 */
    private int readTimeoutMs = 5000;
}
