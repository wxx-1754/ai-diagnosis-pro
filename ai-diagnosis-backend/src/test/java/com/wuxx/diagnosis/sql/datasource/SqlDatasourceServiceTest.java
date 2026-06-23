package com.wuxx.diagnosis.sql.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceOption;
import com.wuxx.diagnosis.sql.domain.SqlDatasourceUpsertRequest;
import com.wuxx.diagnosis.sql.mapper.SqlDatasourceConfigMapper;
import com.wuxx.diagnosis.sql.mapper.SqlDiagnosisRecordMapper;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlDatasourceServiceTest {

    private SqlDatasourceConfigMapper mapper;
    private SqlDiagnosisRecordMapper diagnosisRecordMapper;
    private PasswordCipherService passwordCipherService;
    private DynamicSqlDatasourceFactory datasourceFactory;
    private SqlDatasourceService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SqlDatasourceConfigMapper.class);
        diagnosisRecordMapper = mock(SqlDiagnosisRecordMapper.class);
        passwordCipherService = mock(PasswordCipherService.class);
        datasourceFactory = mock(DynamicSqlDatasourceFactory.class);
        service = new SqlDatasourceService(mapper, diagnosisRecordMapper, passwordCipherService, datasourceFactory);
    }

    @Test
    void optionsScopedToAppAndEnv() {
        SqlDatasourceConfig owned = config("order-svc", "order-db", "prod");
        when(mapper.findEnabledByAppAndEnv("order-svc", "prod")).thenReturn(List.of(owned));

        List<SqlDatasourceOption> options = service.options("order-svc", "prod");

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().getAppId()).isEqualTo("order-svc");
        verify(mapper).findEnabledByAppAndEnv("order-svc", "prod");
    }

    @Test
    void optionsRejectsBlankAppId() {
        assertThatThrownBy(() -> service.options("", "prod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appId");
    }

    @Test
    void getEnabledRejectsDatasourceOwnedByOtherApp() {
        SqlDatasourceConfig config = config("inventory-svc", "order-db", "prod");
        when(mapper.findByCodeAndEnv("order-db", "prod")).thenReturn(config);

        // 数据源归属 inventory-svc，但当前诊断 order-svc → 必须拒绝，杜绝跨应用用库。
        assertThatThrownBy(() -> service.getEnabled("order-db", "order-svc", "prod"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("不属于当前应用");
    }

    @Test
    void getEnabledReturnsConfigWhenAppMatches() {
        SqlDatasourceConfig config = config("order-svc", "order-db", "prod");
        when(mapper.findByCodeAndEnv("order-db", "prod")).thenReturn(config);

        SqlDatasourceConfig result = service.getEnabled("order-db", "order-svc", "prod");

        assertThat(result.getAppId()).isEqualTo("order-svc");
    }

    @Test
    void createEncryptsPasswordAndBindsApp() {
        SqlDatasourceUpsertRequest request = upsertRequest();
        request.setPassword("plain-secret");
        when(mapper.findByCodeAndEnv("order-db", "prod")).thenReturn(null);
        when(passwordCipherService.encrypt("plain-secret")).thenReturn("v1:encrypted");

        service.create(request);

        verify(passwordCipherService).encrypt("plain-secret");
        verify(mapper).insert(any(SqlDatasourceConfig.class));
    }

    @Test
    void updateKeepsExistingJdbcUrlWhenRequestLeavesItBlank() {
        SqlDatasourceConfig existing = config("order-svc", "order-db", "prod");
        existing.setJdbcUrl("jdbc:mysql://localhost:3306/order?useUnicode=true&serverTimezone=Asia/Shanghai");
        existing.setPasswordCipher("v1:existing");
        SqlDatasourceUpsertRequest request = upsertRequest();
        request.setJdbcUrl("");
        request.setPassword("");
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.findByCodeAndEnv("order-db", "prod")).thenReturn(existing);

        service.update(1L, request);

        verify(mapper).update(argThat(config ->
                existing.getJdbcUrl().equals(config.getJdbcUrl())
                        && "v1:existing".equals(config.getPasswordCipher())));
    }

    @Test
    void updateRejectsMaskedJdbcUrlBeforeItCanOverwriteDatabase() {
        SqlDatasourceConfig existing = config("order-svc", "order-db", "prod");
        SqlDatasourceUpsertRequest request = upsertRequest();
        request.setJdbcUrl("jdbc:mysql://localhost:3306/order?<redacted>");
        when(mapper.findById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("脱敏值");
    }

    @Test
    void createStillRequiresJdbcUrl() {
        SqlDatasourceUpsertRequest request = upsertRequest();
        request.setJdbcUrl("");
        request.setPassword("plain-secret");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl不能为空");
    }

    private SqlDatasourceConfig config(String appId, String code, String env) {
        SqlDatasourceConfig config = new SqlDatasourceConfig();
        config.setId(1L);
        config.setDatasourceCode(code);
        config.setDatasourceName(code);
        config.setAppId(appId);
        config.setDbType("MYSQL");
        config.setJdbcUrl("jdbc:mysql://localhost:3306/db");
        config.setUsername("root");
        config.setEnv(env);
        config.setStatus("ENABLED");
        return config;
    }

    private SqlDatasourceUpsertRequest upsertRequest() {
        SqlDatasourceUpsertRequest request = new SqlDatasourceUpsertRequest();
        request.setDatasourceCode("order-db");
        request.setDatasourceName("订单库");
        request.setAppId("order-svc");
        request.setJdbcUrl("jdbc:mysql://localhost:3306/order");
        request.setUsername("root");
        request.setEnv("prod");
        request.setStatus("ENABLED");
        return request;
    }
}
