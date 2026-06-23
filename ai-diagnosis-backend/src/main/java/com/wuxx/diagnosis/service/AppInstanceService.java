package com.wuxx.diagnosis.service;

import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.mapper.AppInstanceMapper;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AppInstanceService {

    private final AppInstanceMapper appInstanceMapper;
    private final PasswordCipherService passwordCipherService;

    public AppInstance getOnlineInstance(String appId, String env) {
        AppInstance instance = appInstanceMapper.findOnlineByAppIdAndEnv(appId, env);
        if (instance == null) {
            throw new IllegalArgumentException("No online app instance found, appId=" + appId + ", env=" + env);
        }
        String accessMode = StringUtils.hasText(instance.getAccessMode())
                ? instance.getAccessMode().trim().toUpperCase()
                : "HTTP";
        if ("HTTP".equals(accessMode)) {
            if (!StringUtils.hasText(instance.getIp()) || instance.getArthasHttpPort() == null) {
                throw new IllegalArgumentException("HTTP 模式缺少 Arthas IP 或端口");
            }
        } else if ("TUNNEL".equals(accessMode)) {
            if (!StringUtils.hasText(instance.getArthasAgentId())) {
                throw new IllegalArgumentException("TUNNEL 模式缺少 arthasAgentId");
            }
        } else {
            throw new IllegalArgumentException("Unsupported Arthas accessMode: " + instance.getAccessMode());
        }
        instance.setAccessMode(accessMode);
        // 密码优先解密 passwordCipher；为空时回退存量 arthasPassword（明文兼容期），保证旧实例可用。
        // 解密后的明文写回 arthasPassword，ArthasHttpCommandExecutor 无需改动即可使用。
        if ("HTTP".equals(accessMode) && StringUtils.hasText(instance.getPasswordCipher())) {
            instance.setArthasPassword(passwordCipherService.decrypt(instance.getPasswordCipher()));
        }
        return instance;
    }
}
