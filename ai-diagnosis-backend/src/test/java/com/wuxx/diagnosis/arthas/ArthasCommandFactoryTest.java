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
        assertThat(factory.buildCommand("threadBlock")).isEqualTo("thread -b");
        assertThat(factory.buildCommand("jvm")).isEqualTo("jvm");
        assertThat(factory.buildCommand("memory")).isEqualTo("memory");
    }

    @Test
    void buildCommandRejectsUnsupportedCommandType() {
        assertThatThrownBy(() -> factory.buildCommand("trace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported commandType");
    }

    @Test
    void buildTraceCommandAllowsOnlyClassAndMethodTemplate() {
        assertThat(factory.buildTraceCommand("com.example.order.OrderController", "createOrder"))
                .isEqualTo("trace com.example.order.OrderController createOrder -n 3");
        assertThatThrownBy(() -> factory.buildTraceCommand("com.example.OrderController;shutdown", "createOrder"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetClass不是合法Java类名");
        assertThatThrownBy(() -> factory.buildTraceCommand("com.example.OrderController", "create-order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetMethod不是合法Java方法名");
    }
}
