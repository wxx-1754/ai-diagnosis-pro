package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.mapper.AppInstanceMapper;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import org.junit.jupiter.api.Test;

class AppInstanceServiceTest {

    private final AppInstanceMapper appInstanceMapper = mock(AppInstanceMapper.class);
    private final PasswordCipherService passwordCipherService = mock(PasswordCipherService.class);
    private final AppInstanceService service = new AppInstanceService(appInstanceMapper, passwordCipherService);

    @Test
    void getOnlineInstanceReturnsHttpInstance() {
        AppInstance instance = instance("HTTP");
        when(appInstanceMapper.findOnlineByAppIdAndEnv("order-service", "test")).thenReturn(instance);

        assertThat(service.getOnlineInstance("order-service", "test")).isSameAs(instance);
    }

    @Test
    void getOnlineInstanceRejectsMissingInstance() {
        when(appInstanceMapper.findOnlineByAppIdAndEnv("missing-service", "test")).thenReturn(null);

        assertThatThrownBy(() -> service.getOnlineInstance("missing-service", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No online app instance found");
    }

    @Test
    void getOnlineInstanceRejectsUnsupportedAccessMode() {
        AppInstance instance = instance("SSH");
        when(appInstanceMapper.findOnlineByAppIdAndEnv("order-service", "test")).thenReturn(instance);

        assertThatThrownBy(() -> service.getOnlineInstance("order-service", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Arthas accessMode");
    }

    @Test
    void getOnlineInstanceReturnsTunnelInstance() {
        AppInstance instance = instance("TUNNEL");
        instance.setIp(null);
        instance.setArthasHttpPort(null);
        instance.setArthasAgentId("order-service-test-01");
        when(appInstanceMapper.findOnlineByAppIdAndEnv("order-service", "test")).thenReturn(instance);

        assertThat(service.getOnlineInstance("order-service", "test")).isSameAs(instance);
    }

    @Test
    void getOnlineInstanceRejectsTunnelWithoutAgentId() {
        AppInstance instance = instance("TUNNEL");
        when(appInstanceMapper.findOnlineByAppIdAndEnv("order-service", "test")).thenReturn(instance);

        assertThatThrownBy(() -> service.getOnlineInstance("order-service", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arthasAgentId");
    }

    @Test
    void getOnlineInstanceDecryptsPasswordCipherIntoArthasPassword() {
        AppInstance instance = instance("HTTP");
        instance.setPasswordCipher("v1:encrypted");
        when(appInstanceMapper.findOnlineByAppIdAndEnv("order-service", "test")).thenReturn(instance);
        when(passwordCipherService.decrypt("v1:encrypted")).thenReturn("plain-secret");

        AppInstance result = service.getOnlineInstance("order-service", "test");

        assertThat(result.getArthasPassword()).isEqualTo("plain-secret");
    }

    private AppInstance instance(String accessMode) {
        AppInstance instance = new AppInstance();
        instance.setAppId("order-service");
        instance.setEnv("test");
        instance.setAccessMode(accessMode);
        instance.setIp("127.0.0.1");
        instance.setArthasHttpPort(8563);
        return instance;
    }
}
