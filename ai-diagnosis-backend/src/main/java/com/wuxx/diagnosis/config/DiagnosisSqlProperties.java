package com.wuxx.diagnosis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "diagnosis.sql")
public class DiagnosisSqlProperties {

    private boolean enabled = true;

    private boolean adminEnabled = false;

    private String adminToken;

    private String encryptionKey;

    private int maxSqlLength = 20000;

    private long connectTimeoutMs = 3000;

    private long validationTimeoutMs = 2000;

    private int queryTimeoutSeconds = 15;

    private int maximumPoolSize = 3;

    /**
     * SQL 诊断证据门禁：trace 调用链中 MyBatis/JDBC 节点的耗时占比达到该百分比才视为“检测到 SQL 问题”，
     * 允许进入 SQL 诊断流程。默认 30。
     */
    private int evidenceMinCostPercent = 30;

    /**
     * SQL 诊断证据门禁：trace 调用链中 MyBatis/JDBC 节点的绝对耗时达到该毫秒数也视为证据。默认 100。
     */
    private int evidenceMinCostMillis = 100;
}
