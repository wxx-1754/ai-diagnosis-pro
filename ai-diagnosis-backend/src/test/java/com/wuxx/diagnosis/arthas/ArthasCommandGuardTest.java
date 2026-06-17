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
        assertThatCode(() -> guard.check("thread -n 1")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("thread -n 5")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("thread -n 10")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("thread 123")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("thread -b")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("jvm")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("memory")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("trace com.demo.OrderService createOrder -n 3")).doesNotThrowAnyException();
    }

    @Test
    void checkRejectsDangerousAndParameterizedCommands() {
        assertThatThrownBy(() -> guard.check("trace com.demo.OrderService create"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("dashboard -n 10"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("thread -n 20"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("thread -n abc"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("thread 0"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("thread abc"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("thread -b 1"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("trace com.demo.OrderService createOrder -n 10"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("trace com.demo.OrderService;shutdown createOrder -n 3"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.check("jvm; shutdown"))
                .isInstanceOf(SecurityException.class);
    }
}
