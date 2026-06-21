package com.wuxx.diagnosis.sql.datasource;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceOption;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceUpsertRequest;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceView;
import com.wuxx.diagnosis.sql.mapper.SqlDatasourceConfigMapper;
import com.wuxx.diagnosis.sql.mapper.SqlDiagnosisRecordMapper;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SqlDatasourceService {

    private final SqlDatasourceConfigMapper mapper;
    private final SqlDiagnosisRecordMapper diagnosisRecordMapper;
    private final PasswordCipherService passwordCipherService;
    private final DynamicSqlDatasourceFactory datasourceFactory;

    public List<SqlDatasourceOption> options(String env) {
        if (!StringUtils.hasText(env)) {
            throw new IllegalArgumentException("env不能为空");
        }
        return mapper.findEnabledByEnv(env.trim()).stream()
                .map(item -> SqlDatasourceOption.builder()
                        .datasourceCode(item.getDatasourceCode())
                        .datasourceName(item.getDatasourceName())
                        .dbType(item.getDbType())
                        .env(item.getEnv())
                        .build())
                .toList();
    }

    public List<SqlDatasourceView> list() {
        return mapper.findAll().stream().map(this::view).toList();
    }

    public SqlDatasourceView create(SqlDatasourceUpsertRequest request) {
        validate(request, true);
        if (mapper.findByCodeAndEnv(request.getDatasourceCode().trim(), request.getEnv().trim()) != null) {
            throw new IllegalStateException("相同环境下数据源编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SqlDatasourceConfig config = fromRequest(request, null);
        config.setPasswordCipher(passwordCipherService.encrypt(request.getPassword()));
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        mapper.insert(config);
        return view(config);
    }

    public SqlDatasourceView update(Long id, SqlDatasourceUpsertRequest request) {
        SqlDatasourceConfig existing = getById(id);
        validate(request, false);
        SqlDatasourceConfig duplicate = mapper.findByCodeAndEnv(request.getDatasourceCode().trim(), request.getEnv().trim());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalStateException("相同环境下数据源编码已存在");
        }
        SqlDatasourceConfig config = fromRequest(request, id);
        config.setPasswordCipher(StringUtils.hasText(request.getPassword())
                ? passwordCipherService.encrypt(request.getPassword())
                : existing.getPasswordCipher());
        config.setCreatedAt(existing.getCreatedAt());
        config.setUpdatedAt(LocalDateTime.now());
        mapper.update(config);
        datasourceFactory.evict(existing.getDatasourceCode(), existing.getEnv());
        datasourceFactory.evict(config.getDatasourceCode(), config.getEnv());
        return view(config);
    }

    public void delete(Long id) {
        SqlDatasourceConfig existing = getById(id);
        if (diagnosisRecordMapper.countByDatasourceCode(existing.getDatasourceCode()) > 0) {
            throw new IllegalStateException("数据源已有诊断记录，只能停用，不能删除");
        }
        mapper.deleteById(id);
        datasourceFactory.evict(existing.getDatasourceCode(), existing.getEnv());
    }

    public void testConnection(Long id) {
        SqlDatasourceConfig config = getById(id);
        try (HikariDataSource dataSource = datasourceFactory.createUncached(config)) {
            new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
        }
    }

    public SqlDatasourceConfig getEnabled(String datasourceCode, String env) {
        SqlDatasourceConfig config = mapper.findByCodeAndEnv(datasourceCode, env);
        if (config == null || !"ENABLED".equals(config.getStatus())) {
            throw new IllegalArgumentException("数据源不存在或未启用：" + datasourceCode + ", env=" + env);
        }
        if (!"MYSQL".equals(config.getDbType())) {
            throw new IllegalArgumentException("当前仅支持 MYSQL 数据源");
        }
        return config;
    }

    private SqlDatasourceConfig getById(Long id) {
        SqlDatasourceConfig config = mapper.findById(id);
        if (config == null) {
            throw new IllegalArgumentException("SQL数据源不存在，id=" + id);
        }
        return config;
    }

    private void validate(SqlDatasourceUpsertRequest request, boolean passwordRequired) {
        if (passwordRequired && !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("password不能为空");
        }
        if (!request.getDatasourceCode().matches("[A-Za-z][A-Za-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("datasourceCode格式无效");
        }
        if (!request.getEnv().matches("[A-Za-z][A-Za-z0-9_-]{1,31}")) {
            throw new IllegalArgumentException("env格式无效");
        }
        String status = normalizeStatus(request.getStatus());
        request.setStatus(status);
        if (!request.getJdbcUrl().startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("当前只允许 jdbc:mysql:// 数据源");
        }
    }

    private SqlDatasourceConfig fromRequest(SqlDatasourceUpsertRequest request, Long id) {
        SqlDatasourceConfig config = new SqlDatasourceConfig();
        config.setId(id);
        config.setDatasourceCode(request.getDatasourceCode().trim());
        config.setDatasourceName(request.getDatasourceName().trim());
        config.setDbType("MYSQL");
        config.setJdbcUrl(request.getJdbcUrl().trim());
        config.setUsername(request.getUsername().trim());
        config.setEnv(request.getEnv().trim());
        config.setStatus(normalizeStatus(request.getStatus()));
        return config;
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "ENABLED";
        if (!List.of("ENABLED", "DISABLED").contains(value)) {
            throw new IllegalArgumentException("status只能是ENABLED或DISABLED");
        }
        return value;
    }

    private SqlDatasourceView view(SqlDatasourceConfig config) {
        return SqlDatasourceView.builder()
                .id(config.getId())
                .datasourceCode(config.getDatasourceCode())
                .datasourceName(config.getDatasourceName())
                .dbType(config.getDbType())
                .jdbcUrlMasked(maskJdbcUrl(config.getJdbcUrl()))
                .username(config.getUsername())
                .env(config.getEnv())
                .status(config.getStatus())
                .passwordConfigured(StringUtils.hasText(config.getPasswordCipher()))
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private String maskJdbcUrl(String jdbcUrl) {
        int query = jdbcUrl.indexOf('?');
        return query < 0 ? jdbcUrl : jdbcUrl.substring(0, query) + "?<redacted>";
    }
}
