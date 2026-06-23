package com.wuxx.diagnosis.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.wuxx.diagnosis.config.DiagnosisAppInstanceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * App Instance 管理接口令牌校验。
 * 常量时间比较，防止时序攻击；与 SqlAdminAccessGuard 同构。
 */
@Component
@RequiredArgsConstructor
public class AppInstanceAdminAccessGuard {

    private final DiagnosisAppInstanceProperties properties;

    public void check(String providedToken) {
        if (!properties.isAdminEnabled()) {
            throw new SecurityException("App Instance 管理接口未启用");
        }
        if (!StringUtils.hasText(properties.getAdminToken())) {
            throw new IllegalStateException("App Instance 管理接口已启用但未配置管理令牌");
        }
        byte[] expected = properties.getAdminToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = String.valueOf(providedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("App Instance 管理令牌无效");
        }
    }
}
