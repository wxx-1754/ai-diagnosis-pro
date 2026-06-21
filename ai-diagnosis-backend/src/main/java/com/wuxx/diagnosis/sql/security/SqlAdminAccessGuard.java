package com.wuxx.diagnosis.sql.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SqlAdminAccessGuard {

    private final DiagnosisSqlProperties properties;

    public void check(String providedToken) {
        if (!properties.isAdminEnabled()) {
            throw new SecurityException("SQL 数据源管理接口未启用");
        }
        if (!StringUtils.hasText(properties.getAdminToken())) {
            throw new IllegalStateException("SQL 管理接口已启用但未配置管理令牌");
        }
        byte[] expected = properties.getAdminToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = String.valueOf(providedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("SQL 数据源管理令牌无效");
        }
    }
}
