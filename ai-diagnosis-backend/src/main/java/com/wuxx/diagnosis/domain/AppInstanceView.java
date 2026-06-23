package com.wuxx.diagnosis.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * App Instance 脱敏视图，用于管理接口返回。
 * 不回传密码明文/密文，仅回 passwordConfigured 标记；Arthas 地址回显但不含认证信息。
 */
@Data
@Builder
public class AppInstanceView {

    private Long id;
    private String appId;
    private String appName;
    private String env;
    private String ip;
    private Integer arthasHttpPort;
    private String arthasUsername;
    private String arthasAgentId;
    private String accessMode;
    private String status;
    private String arthasUrl;
    private boolean passwordConfigured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
