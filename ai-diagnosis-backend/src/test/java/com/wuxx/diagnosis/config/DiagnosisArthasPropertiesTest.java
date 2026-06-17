package com.wuxx.diagnosis.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiagnosisArthasPropertiesTest {

    @Test
    void defaultReadTimeoutLeavesEnoughTimeForTraceSampling() {
        DiagnosisArthasProperties properties = new DiagnosisArthasProperties();

        assertThat(properties.getReadTimeoutMs()).isEqualTo(60000);
    }
}
