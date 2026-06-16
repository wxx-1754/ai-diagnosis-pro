package com.wuxx.diagnosis.arthas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArthasCommandFactoryTest {

    private final ArthasCommandFactory factory = new ArthasCommandFactory();

    @Test
    void buildCommandMapsAllowedCommandTypes() {
        assertThat(factory.buildCommand("dashboard")).isEqualTo("dashboard -n 1");
        assertThat(factory.buildCommand("thread")).isEqualTo("thread");
        assertThat(factory.buildCommand("topThread")).isEqualTo("thread -n 5");
        assertThat(factory.buildCommand("jvm")).isEqualTo("jvm");
        assertThat(factory.buildCommand("memory")).isEqualTo("memory");
    }

    @Test
    void buildCommandRejectsUnsupportedCommandType() {
        assertThatThrownBy(() -> factory.buildCommand("trace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported commandType");
    }
}
