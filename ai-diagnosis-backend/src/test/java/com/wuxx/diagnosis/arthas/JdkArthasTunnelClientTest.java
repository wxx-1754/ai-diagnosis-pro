package com.wuxx.diagnosis.arthas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdkArthasTunnelClientTest {

    @Test
    void buildsConnectArthasUriWithEncodedAgentId() {
        assertThat(JdkArthasTunnelClient.buildUri(
                "ws://tunnel-server:7777/ws",
                "order/service prod-01"
        ).toString()).isEqualTo(
                "ws://tunnel-server:7777/ws?method=connectArthas&id=order%2Fservice%20prod-01"
        );
    }

    @Test
    void removesAnsiEchoAndFinalPrompt() {
        String raw = "\u001B[32mjvm\r\nJVM output\r\n[arthas@123]$ \u001B[0m";

        assertThat(JdkArthasTunnelClient.cleanCommandOutput(raw, "jvm"))
                .isEqualTo("JVM output");
    }
}
