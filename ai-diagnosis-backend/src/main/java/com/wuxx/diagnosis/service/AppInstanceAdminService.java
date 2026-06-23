package com.wuxx.diagnosis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.wuxx.diagnosis.arthas.ArthasCommandExecutor;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.AppInstanceTestResult;
import com.wuxx.diagnosis.domain.AppInstanceUpsertRequest;
import com.wuxx.diagnosis.domain.AppInstanceView;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.mapper.AppInstanceMapper;
import com.wuxx.diagnosis.mapper.ArthasCommandRecordMapper;
import com.wuxx.diagnosis.sql.security.PasswordCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * App Instance 管理面服务：list/create/update/delete + Arthas HTTP/Tunnel 连通性测试。
 * 密码经 {@link PasswordCipherService}（AES-GCM）加密；View 脱敏；删除前校验诊断记录引用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInstanceAdminService {

    private static final List<String> ACCESS_MODES = List.of("HTTP", "TUNNEL");
    private static final List<String> STATUSES = List.of("ONLINE", "OFFLINE");

    private final AppInstanceMapper appInstanceMapper;
    private final ArthasCommandRecordMapper arthasCommandRecordMapper;
    private final PasswordCipherService passwordCipherService;
    private final ArthasCommandExecutor commandExecutor;

    public List<AppInstanceView> list() {
        return appInstanceMapper.findAll().stream().map(this::view).toList();
    }

    public AppInstanceView create(AppInstanceUpsertRequest request) {
        validate(request);
        if (appInstanceMapper.findByAppIdAndEnv(request.getAppId().trim(), request.getEnv().trim()) != null) {
            throw new IllegalStateException("相同 appId+env 的实例已存在");
        }
        checkDuplicateAgentId(request, null);
        LocalDateTime now = LocalDateTime.now();
        AppInstance instance = fromRequest(request, null);
        applyPassword(instance, request, null);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        appInstanceMapper.insert(instance);
        return view(instance);
    }

    public AppInstanceView update(Long id, AppInstanceUpsertRequest request) {
        AppInstance existing = getById(id);
        validate(request);
        AppInstance duplicate = appInstanceMapper.findByAppIdAndEnv(request.getAppId().trim(), request.getEnv().trim());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalStateException("相同 appId+env 的实例已存在");
        }
        checkDuplicateAgentId(request, id);
        AppInstance instance = fromRequest(request, id);
        applyPassword(instance, request, existing);
        instance.setCreatedAt(existing.getCreatedAt());
        instance.setUpdatedAt(LocalDateTime.now());
        appInstanceMapper.update(instance);
        return view(instance);
    }

    public void delete(Long id) {
        AppInstance existing = getById(id);
        if (arthasCommandRecordMapper.countByAppIdAndEnv(existing.getAppId(), existing.getEnv()) > 0) {
            throw new IllegalStateException("该实例已有 Arthas 诊断记录，只能停用（OFFLINE），不能删除");
        }
        appInstanceMapper.deleteById(id);
    }

    public AppInstanceTestResult test(Long id) {
        AppInstance existing = getById(id);
        long start = System.currentTimeMillis();
        try {
            validateStoredInstance(existing);
            if ("HTTP".equalsIgnoreCase(existing.getAccessMode())) {
                existing.setArthasPassword(resolvePassword(existing));
            }
            ArthasExecuteResponse response = commandExecutor.execute(
                    existing,
                    "ARTHAS-TEST-" + UUID.randomUUID().toString().replace("-", ""),
                    "version",
                    "jvm"
            );
            long latency = System.currentTimeMillis() - start;
            if (response.isSuccess()) {
                return new AppInstanceTestResult(true, latency,
                        "TUNNEL".equalsIgnoreCase(existing.getAccessMode())
                                ? "Tunnel 连接成功，Agent 可执行命令"
                                : "HTTP 连接成功，Arthas 可执行命令");
            }
            return new AppInstanceTestResult(false, latency, response.getErrorMessage());
        } catch (Exception exception) {
            long latency = System.currentTimeMillis() - start;
            log.warn("App instance connection test failed, id={}, accessMode={}, message={}",
                    id, existing.getAccessMode(), exception.getMessage());
            return new AppInstanceTestResult(false, latency, "无法连接：" + exception.getMessage());
        }
    }

    private AppInstance getById(Long id) {
        AppInstance instance = appInstanceMapper.findById(id);
        if (instance == null) {
            throw new IllegalArgumentException("App Instance 不存在，id=" + id);
        }
        return instance;
    }

    private void validate(AppInstanceUpsertRequest request) {
        if (!request.getAppId().matches("[A-Za-z][A-Za-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("appId 格式无效（字母开头，2-64 位字母数字_-）");
        }
        if (!request.getEnv().matches("[A-Za-z][A-Za-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("env 格式无效");
        }
        String accessMode = normalize(request.getAccessMode(), "HTTP", ACCESS_MODES, "accessMode");
        request.setAccessMode(accessMode);
        String status = normalize(request.getStatus(), "ONLINE", STATUSES, "status");
        request.setStatus(status);
        if ("HTTP".equals(accessMode)) {
            if (!StringUtils.hasText(request.getIp())
                    || !request.getIp().matches("[A-Za-z0-9.\\-]{1,64}")) {
                throw new IllegalArgumentException("HTTP 模式 ip 格式无效");
            }
            if (request.getArthasHttpPort() == null
                    || request.getArthasHttpPort() < 1
                    || request.getArthasHttpPort() > 65535) {
                throw new IllegalArgumentException("HTTP 模式 arthasHttpPort 无效");
            }
        } else if (!StringUtils.hasText(request.getArthasAgentId())
                || !request.getArthasAgentId().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("TUNNEL 模式 arthasAgentId 无效");
        }
    }

    private String normalize(String value, String defaultValue, List<String> allowed, String field) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " 只能是 " + allowed);
        }
        return normalized;
    }

    private AppInstance fromRequest(AppInstanceUpsertRequest request, Long id) {
        AppInstance instance = new AppInstance();
        instance.setId(id);
        instance.setAppId(request.getAppId().trim());
        instance.setAppName(request.getAppName().trim());
        instance.setEnv(request.getEnv().trim());
        boolean tunnel = "TUNNEL".equals(request.getAccessMode());
        instance.setIp(tunnel ? null : request.getIp().trim());
        instance.setArthasHttpPort(tunnel ? null : request.getArthasHttpPort());
        instance.setArthasUsername(!tunnel && StringUtils.hasText(request.getArthasUsername())
                ? request.getArthasUsername().trim()
                : null);
        instance.setArthasAgentId(StringUtils.hasText(request.getArthasAgentId()) ? request.getArthasAgentId().trim() : null);
        instance.setAccessMode(request.getAccessMode());
        instance.setStatus(request.getStatus());
        return instance;
    }

    /**
     * 密码落库：传入明文则加密写 passwordCipher 并清空 arthasPassword；
     * 留空则保留存量（优先 passwordCipher，回退 arthasPassword）以维持兼容。
     */
    private void applyPassword(AppInstance instance, AppInstanceUpsertRequest request, AppInstance existing) {
        if ("TUNNEL".equals(instance.getAccessMode())) {
            instance.setPasswordCipher(null);
            instance.setArthasPassword(null);
            return;
        }
        if (StringUtils.hasText(request.getArthasPassword())) {
            instance.setPasswordCipher(passwordCipherService.encrypt(request.getArthasPassword()));
            instance.setArthasPassword(null);
            return;
        }
        if (existing != null) {
            instance.setPasswordCipher(existing.getPasswordCipher());
            instance.setArthasPassword(existing.getArthasPassword());
        } else {
            instance.setPasswordCipher(null);
            instance.setArthasPassword(null);
        }
    }

    /** 优先解密 passwordCipher，否则回退存量 arthasPassword（明文兼容期）。 */
    private String resolvePassword(AppInstance instance) {
        if (StringUtils.hasText(instance.getPasswordCipher())) {
            return passwordCipherService.decrypt(instance.getPasswordCipher());
        }
        return instance.getArthasPassword();
    }

    private AppInstanceView view(AppInstance instance) {
        boolean tunnel = "TUNNEL".equalsIgnoreCase(instance.getAccessMode());
        return AppInstanceView.builder()
                .id(instance.getId())
                .appId(instance.getAppId())
                .appName(instance.getAppName())
                .env(instance.getEnv())
                .ip(instance.getIp())
                .arthasHttpPort(instance.getArthasHttpPort())
                .arthasUsername(instance.getArthasUsername())
                .arthasAgentId(instance.getArthasAgentId())
                .accessMode(instance.getAccessMode())
                .status(instance.getStatus())
                .arthasUrl(tunnel
                        ? "tunnel://" + nullToEmpty(instance.getArthasAgentId())
                        : "http://" + instance.getIp() + ":" + instance.getArthasHttpPort())
                .passwordConfigured(!tunnel && (StringUtils.hasText(instance.getPasswordCipher())
                        || StringUtils.hasText(instance.getArthasPassword()))
                )
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void checkDuplicateAgentId(AppInstanceUpsertRequest request, Long currentId) {
        if (!"TUNNEL".equals(request.getAccessMode()) || !StringUtils.hasText(request.getArthasAgentId())) {
            return;
        }
        AppInstance duplicate = appInstanceMapper.findByArthasAgentId(request.getArthasAgentId().trim());
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentId)) {
            throw new IllegalStateException("arthasAgentId 已被其他实例使用");
        }
    }

    private void validateStoredInstance(AppInstance instance) {
        if ("TUNNEL".equalsIgnoreCase(instance.getAccessMode())) {
            if (!StringUtils.hasText(instance.getArthasAgentId())) {
                throw new IllegalArgumentException("TUNNEL 模式缺少 arthasAgentId");
            }
            return;
        }
        if (!StringUtils.hasText(instance.getIp()) || instance.getArthasHttpPort() == null) {
            throw new IllegalArgumentException("HTTP 模式缺少 IP 或端口");
        }
    }
}
