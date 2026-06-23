package com.wuxx.diagnosis.arthas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import org.junit.jupiter.api.Test;

class ArthasTunnelCommandExecutorTest {

    @Test
    void executesCommandAgainstConfiguredAgentId() throws Exception {
        ArthasTunnelClient client = mock(ArthasTunnelClient.class);
        DiagnosisArthasProperties properties = new DiagnosisArthasProperties();
        when(client.execute("order-service-test-01", "jvm", 30000)).thenReturn("JVM output");
        ArthasTunnelCommandExecutor executor =
                new ArthasTunnelCommandExecutor(client, new ArthasCommandGuard(), properties);

        ArthasExecuteResponse response = executor.execute(instance(), "REQ-1", "jvm", "jvm");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutput()).isEqualTo("JVM output");
        verify(client).execute("order-service-test-01", "jvm", 30000);
    }

    @Test
    void returnsFailureWhenAgentIsOffline() throws Exception {
        ArthasTunnelClient client = mock(ArthasTunnelClient.class);
        when(client.execute("order-service-test-01", "jvm", 30000))
                .thenThrow(new IllegalStateException("Can not find arthas agent by id"));
        ArthasTunnelCommandExecutor executor =
                new ArthasTunnelCommandExecutor(client, new ArthasCommandGuard(), new DiagnosisArthasProperties());

        ArthasExecuteResponse response = executor.execute(instance(), "REQ-2", "jvm", "jvm");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Can not find arthas agent");
    }

    private AppInstance instance() {
        AppInstance instance = new AppInstance();
        instance.setAppId("order-service");
        instance.setEnv("test");
        instance.setAccessMode("TUNNEL");
        instance.setArthasAgentId("order-service-test-01");
        return instance;
    }
}
