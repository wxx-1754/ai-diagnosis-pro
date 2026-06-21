package com.wuxx.diagnosis.sql.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import org.junit.jupiter.api.Test;

class PasswordCipherServiceTest {

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        DiagnosisSqlProperties properties = new DiagnosisSqlProperties();
        properties.setEncryptionKey("test-only-encryption-key");
        PasswordCipherService service = new PasswordCipherService(properties);

        String first = service.encrypt("secret");
        String second = service.encrypt("secret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("secret");
        assertThat(service.decrypt(second)).isEqualTo("secret");
    }

    @Test
    void rejectsMissingKey() {
        PasswordCipherService service = new PasswordCipherService(new DiagnosisSqlProperties());
        assertThatThrownBy(() -> service.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("加密失败");
    }
}
