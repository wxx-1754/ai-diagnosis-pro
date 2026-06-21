package com.wuxx.diagnosis.arthas;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArthasUnrestrictedCommandGuardTest {

    private final ArthasCommandGuard guard = new ArthasCommandGuard();

    @Test
    void acceptsRawWatchAndOgnlCommands() {
        assertThatCode(() -> guard.checkUnrestricted(
                "watch org.apache.ibatis.executor.BaseExecutor query '{params}' -n 1 -x 3", 4000
        )).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkUnrestricted("@java.lang.System@getProperties()", 4000))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyOversizedAndMultilineCommands() {
        assertThatThrownBy(() -> guard.checkUnrestricted(" ", 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.checkUnrestricted("watch " + "x".repeat(101), 100))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.checkUnrestricted("watch A m\nshutdown", 100))
                .isInstanceOf(SecurityException.class);
    }
}
