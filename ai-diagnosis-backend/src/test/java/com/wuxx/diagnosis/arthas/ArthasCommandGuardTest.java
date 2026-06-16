package com.wuxx.diagnosis.arthas;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArthasCommandGuardTest {

    private final ArthasCommandGuard guard = new ArthasCommandGuard();

    @Test
    void checkAllowsOnlyStageOneCommands() {
        assertThatCode(() -> guard.check("dashboard -n 1")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("thread")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("thread -n 5")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("jvm")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("memory")).doesNotThrowAnyException();
    }

    @Test
    void checkRejectsDangerousAndParameterizedCommands() {
        assertThatThrownBy(() -> guard.check("trace com.demo.OrderService create"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("dashboard -n 10"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("thread -n 20"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("jvm; shutdown"))
                .isInstanceOf(SecurityException.class);
    }
}
