package com.wuxx.diagnosis.sql.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DynamicSqlDatasourceFactory {

    private final PasswordCipherService passwordCipherService;
    private final DiagnosisSqlProperties properties;
    private final Map<String, HikariDataSource> cache = new ConcurrentHashMap<>();

    public JdbcTemplate getJdbcTemplate(SqlDatasourceConfig config) {
        HikariDataSource dataSource = cache.computeIfAbsent(key(config), ignored -> create(config));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(properties.getQueryTimeoutSeconds());
        return jdbcTemplate;
    }

    public HikariDataSource createUncached(SqlDatasourceConfig config) {
        return create(config);
    }

    public void evict(String datasourceCode, String env) {
        HikariDataSource removed = cache.remove(datasourceCode + ":" + env);
        if (removed != null) {
            removed.close();
        }
    }

    private HikariDataSource create(SqlDatasourceConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(passwordCipherService.decrypt(config.getPasswordCipher()));
        hikari.setReadOnly(true);
        hikari.setAutoCommit(true);
        hikari.setMaximumPoolSize(Math.max(1, Math.min(properties.getMaximumPoolSize(), 5)));
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(properties.getConnectTimeoutMs());
        hikari.setValidationTimeout(properties.getValidationTimeoutMs());
        hikari.setInitializationFailTimeout(properties.getConnectTimeoutMs());
        hikari.setPoolName("sql-diagnosis-" + safe(config.getDatasourceCode()) + "-" + safe(config.getEnv()));
        return new HikariDataSource(hikari);
    }

    private String key(SqlDatasourceConfig config) {
        return config.getDatasourceCode() + ":" + config.getEnv();
    }

    private String safe(String value) {
        return String.valueOf(value).replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
