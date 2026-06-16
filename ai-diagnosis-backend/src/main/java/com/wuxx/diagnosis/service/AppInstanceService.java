package com.wuxx.diagnosis.service;

import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.mapper.AppInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppInstanceService {

    private final AppInstanceMapper appInstanceMapper;

    public AppInstance getOnlineInstance(String appId, String env) {
        AppInstance instance = appInstanceMapper.findOnlineByAppIdAndEnv(appId, env);
        if (instance == null) {
            throw new IllegalArgumentException("No online app instance found, appId=" + appId + ", env=" + env);
        }
        // 第一阶段只支持直连 Arthas HTTP；TUNNEL 字段先保留，避免误把未实现链路放进执行面。
        if (!"HTTP".equalsIgnoreCase(instance.getAccessMode())) {
            throw new IllegalArgumentException("Unsupported Arthas accessMode: " + instance.getAccessMode());
        }
        return instance;
    }
}
