package com.wuxx.diagnosis.arthas;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wuxx.diagnosis.domain.AppInstance;
import org.junit.jupiter.api.Test;

class ArthasRoutingCommandExecutorTest {

    private final ArthasHttpCommandExecutor httpExecutor = mock(ArthasHttpCommandExecutor.class);
    private final ArthasTunnelCommandExecutor tunnelExecutor = mock(ArthasTunnelCommandExecutor.class);
    private final ArthasRoutingCommandExecutor router =
            new ArthasRoutingCommandExecutor(httpExecutor, tunnelExecutor);

    @Test
    void routesHttpInstanceToHttpExecutor() {
        AppInstance instance = instance("HTTP");

        router.execute(instance, "REQ-HTTP", "jvm", "jvm");

        verify(httpExecutor).execute(instance, "REQ-HTTP", "jvm", "jvm");
    }

    @Test
    void routesTunnelInstanceToTunnelExecutor() {
        AppInstance instance = instance("TUNNEL");

        router.execute(instance, "REQ-TUNNEL", "dashboard -n 1", "dashboard");

        verify(tunnelExecutor).execute(instance, "REQ-TUNNEL", "dashboard -n 1", "dashboard");
    }

    private AppInstance instance(String accessMode) {
        AppInstance instance = new AppInstance();
        instance.setAccessMode(accessMode);
        return instance;
    }
}
