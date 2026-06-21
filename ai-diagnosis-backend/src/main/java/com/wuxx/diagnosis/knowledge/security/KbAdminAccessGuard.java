package com.wuxx.diagnosis.knowledge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.wuxx.diagnosis.knowledge.config.KnowledgeBaseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class KbAdminAccessGuard {

    private final KnowledgeBaseProperties properties;

    public void check(String providedToken) {
        if (!properties.isAdminEnabled()) {
            throw new SecurityException("知识库管理接口未启用");
        }
        if (!StringUtils.hasText(properties.getAdminToken())) {
            throw new IllegalStateException("知识库管理接口已启用但未配置管理令牌");
        }
        byte[] expected = properties.getAdminToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = String.valueOf(providedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("知识库管理令牌无效");
        }
    }
}
