package com.wuxx.diagnosis.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiagnosisArthasPropertiesTest {

    @Test
    void defaultReadTimeoutLeavesEnoughTimeForTraceSampling() {
        DiagnosisArthasProperties properties = new DiagnosisArthasProperties();

        assertThat(properties.getTraceExecTimeoutMs()).isEqualTo(90000);
        assertThat(properties.getReadTimeoutMs())
                .isGreaterThan(properties.getTraceExecTimeoutMs());
        assertThat(properties.getTunnel().getWsUrl()).isEqualTo("ws://127.0.0.1:7777/ws");
        assertThat(properties.getTunnel().getWebUrl()).isEqualTo("http://127.0.0.1:8080");
    }
}
