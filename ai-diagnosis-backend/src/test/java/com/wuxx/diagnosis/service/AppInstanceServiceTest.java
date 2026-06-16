package com.wuxx.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.mapper.AppInstanceMapper;
import org.junit.jupiter.api.Test;

class AppInstanceServiceTest {

    @Test
    void getOnlineInstanceReturnsHttpInstance() {
        AppInstance instance = instance("HTTP");
        AppInstanceService service = new AppInstanceService((appId, env) -> instance);

        assertThat(service.getOnlineInstance("order-service", "test")).isSameAs(instance);
    }

    @Test
    void getOnlineInstanceRejectsMissingInstance() {
        AppInstanceService service = new AppInstanceService((appId, env) -> null);

        assertThatThrownBy(() -> service.getOnlineInstance("missing-service", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No online app instance found");
    }

    @Test
    void getOnlineInstanceRejectsUnsupportedAccessMode() {
        AppInstanceService service = new AppInstanceService((appId, env) -> instance("TUNNEL"));

        assertThatThrownBy(() -> service.getOnlineInstance("order-service", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Arthas accessMode");
    }

    private AppInstance instance(String accessMode) {
        AppInstance instance = new AppInstance();
        instance.setAppId("order-service");
        instance.setEnv("test");
        instance.setAccessMode(accessMode);
        return instance;
    }
}
