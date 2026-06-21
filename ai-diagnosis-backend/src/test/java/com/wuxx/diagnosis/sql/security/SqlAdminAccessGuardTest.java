package com.wuxx.diagnosis.sql.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import org.junit.jupiter.api.Test;

class SqlAdminAccessGuardTest {

    @Test
    void requiresEnabledFlagAndMatchingToken() {
        DiagnosisSqlProperties properties = new DiagnosisSqlProperties();
        properties.setAdminToken("admin-token");
        SqlAdminAccessGuard guard = new SqlAdminAccessGuard(properties);

        assertThatThrownBy(() -> guard.check("admin-token"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("未启用");

        properties.setAdminEnabled(true);
        assertThatThrownBy(() -> guard.check("wrong"))
                .isInstanceOf(SecurityException.class);
        assertThatCode(() -> guard.check("admin-token")).doesNotThrowAnyException();
    }
}
