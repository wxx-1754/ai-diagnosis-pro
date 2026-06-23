package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wuxx.diagnosis.arthas.ArthasCommandExecutor;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.AppInstanceUpsertRequest;
import com.wuxx.diagnosis.domain.AppInstanceView;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.mapper.AppInstanceMapper;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppInstanceAdminServiceTest {

    private AppInstanceMapper appInstanceMapper;
    private ArthasCommandRecordMapper arthasCommandRecordMapper;
    private PasswordCipherService passwordCipherService;
    private ArthasCommandExecutor commandExecutor;
    private AppInstanceAdminService service;

    @BeforeEach
    void setUp() {
        appInstanceMapper = mock(AppInstanceMapper.class);
        arthasCommandRecordMapper = mock(ArthasCommandRecordMapper.class);
        passwordCipherService = mock(PasswordCipherService.class);
        commandExecutor = mock(ArthasCommandExecutor.class);
        service = new AppInstanceAdminService(appInstanceMapper, arthasCommandRecordMapper,
                passwordCipherService, commandExecutor);
    }

    @Test
    void createEncryptsPasswordAndClearsPlaintext() {
        AppInstanceUpsertRequest request = upsertRequest();
        request.setArthasPassword("plain-secret");
        when(appInstanceMapper.findByAppIdAndEnv("order-svc", "prod")).thenReturn(null);
        when(passwordCipherService.encrypt("plain-secret")).thenReturn("v1:encrypted");
        doAnswer(invocation -> {
            ((AppInstance) invocation.getArgument(0)).setId(1L);
            return 1;
        }).when(appInstanceMapper).insert(any(AppInstance.class));

        AppInstanceView view = service.create(request);

        assertThat(view.isPasswordConfigured()).isTrue();
        verify(passwordCipherService).encrypt("plain-secret");
        verify(appInstanceMapper).insert(any(AppInstance.class));
    }

    @Test
    void createRejectsDuplicateAppIdEnv() {
        AppInstanceUpsertRequest request = upsertRequest();
        when(appInstanceMapper.findByAppIdAndEnv("order-svc", "prod")).thenReturn(new AppInstance());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void updateKeepsExistingCipherWhenPasswordBlank() {
        AppInstance existing = new AppInstance();
        existing.setId(2L);
        existing.setAppId("order-svc");
        existing.setEnv("prod");
        existing.setPasswordCipher("v1:existing");
        existing.setCreatedAt(LocalDateTime.now());
        AppInstanceUpsertRequest request = upsertRequest();
        request.setArthasPassword(""); // 留空 → 保留原密文
        when(appInstanceMapper.findById(2L)).thenReturn(existing);
        when(appInstanceMapper.findByAppIdAndEnv("order-svc", "prod")).thenReturn(existing);

        service.update(2L, request);

        verify(passwordCipherService, org.mockito.Mockito.never()).encrypt(any());
        verify(appInstanceMapper).update(any(AppInstance.class));
    }

    @Test
    void deleteBlocksWhenCommandRecordsExist() {
        AppInstance existing = new AppInstance();
        existing.setAppId("order-svc");
        existing.setEnv("prod");
        when(appInstanceMapper.findById(3L)).thenReturn(existing);
        when(arthasCommandRecordMapper.countByAppIdAndEnv("order-svc", "prod")).thenReturn(5L);

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能停用");
    }

    @Test
    void createTunnelInstanceDoesNotRequireHttpAddress() {
        AppInstanceUpsertRequest request = upsertRequest();
        request.setAccessMode("TUNNEL");
        request.setIp(null);
        request.setArthasHttpPort(null);
        request.setArthasAgentId("order-service-prod-01");
        when(appInstanceMapper.findByAppIdAndEnv("order-svc", "prod")).thenReturn(null);
        when(appInstanceMapper.findByArthasAgentId("order-service-prod-01")).thenReturn(null);

        AppInstanceView view = service.create(request);

        assertThat(view.getAccessMode()).isEqualTo("TUNNEL");
        assertThat(view.getArthasUrl()).isEqualTo("tunnel://order-service-prod-01");
        assertThat(view.getIp()).isNull();
        assertThat(view.getArthasHttpPort()).isNull();
    }

    @Test
    void testTunnelInstanceExecutesVersionThroughRouter() {
        AppInstance existing = new AppInstance();
        existing.setId(4L);
        existing.setAppId("order-svc");
        existing.setEnv("prod");
        existing.setAccessMode("TUNNEL");
        existing.setArthasAgentId("order-service-prod-01");
        when(appInstanceMapper.findById(4L)).thenReturn(existing);
        when(commandExecutor.execute(any(), any(), any(), any())).thenReturn(
                ArthasExecuteResponse.builder().success(true).output("4.0.0").build());

        assertThat(service.test(4L).ok()).isTrue();
        verify(commandExecutor).execute(org.mockito.ArgumentMatchers.eq(existing),
                org.mockito.ArgumentMatchers.startsWith("ARTHAS-TEST-"),
                org.mockito.ArgumentMatchers.eq("version"),
                org.mockito.ArgumentMatchers.eq("jvm"));
    }

    private AppInstanceUpsertRequest upsertRequest() {
        AppInstanceUpsertRequest request = new AppInstanceUpsertRequest();
        request.setAppId("order-svc");
        request.setAppName("订单服务");
        request.setEnv("prod");
        request.setIp("10.0.0.1");
        request.setArthasHttpPort(8563);
        request.setArthasUsername("arthas");
        request.setAccessMode("HTTP");
        return request;
    }
}
