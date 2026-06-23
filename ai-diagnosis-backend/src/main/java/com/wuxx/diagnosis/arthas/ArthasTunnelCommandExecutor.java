package com.wuxx.diagnosis.arthas;

import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArthasTunnelCommandExecutor implements ArthasCommandExecutor {

    private final ArthasTunnelClient tunnelClient;
    private final ArthasCommandGuard commandGuard;
    private final DiagnosisArthasProperties properties;

    @Override
    public ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType) {
        long start = System.currentTimeMillis();
        try {
            checkCommand(instance, command, commandType);
            int timeoutMillis = resolveExecTimeout(command);
            log.info("Calling Arthas Tunnel, requestNo={}, agentId={}, commandType={}",
                    requestNo, instance.getArthasAgentId(), commandType);
            String output = tunnelClient.execute(instance.getArthasAgentId(), command, timeoutMillis);
            return response(instance, requestNo, command, true, output, null, start);
        } catch (Exception exception) {
            log.warn("Arthas Tunnel call failed, requestNo={}, appId={}, env={}, agentId={}, message={}",
                    requestNo, instance.getAppId(), instance.getEnv(), instance.getArthasAgentId(),
                    exception.getMessage());
            log.debug("Arthas Tunnel failure detail, requestNo={}", requestNo, exception);
            return response(instance, requestNo, command, false, null, rootMessage(exception), start);
        }
    }

    private void checkCommand(AppInstance instance, String command, String commandType) {
        if (!"rawArthas".equals(commandType)) {
            commandGuard.check(command);
            return;
        }
        if (!properties.isUnrestrictedAiCommandsEnabled()) {
            throw new SecurityException("AI 原生 Arthas 命令未启用");
        }
        boolean environmentAllowed = properties.getUnrestrictedAiEnvironments().stream()
                .anyMatch(env -> env.equalsIgnoreCase(instance.getEnv()));
        if (!environmentAllowed) {
            throw new SecurityException("当前环境禁止 AI 原生 Arthas 命令：" + instance.getEnv());
        }
        commandGuard.checkUnrestricted(command, properties.getUnrestrictedAiMaxCommandLength());
    }

    private int resolveExecTimeout(String command) {
        if (command != null && properties.isUnrestrictedAiCommandsEnabled()) {
            String prefix = command.trim().split("\\s+", 2)[0];
            if ("watch".equals(prefix) || "trace".equals(prefix) || "monitor".equals(prefix)
                    || "tt".equals(prefix) || "stack".equals(prefix)) {
                return properties.getUnrestrictedAiExecTimeoutMs();
            }
        }
        return command != null && command.trim().startsWith("trace ")
                ? properties.getTraceExecTimeoutMs()
                : properties.getExecTimeoutMs();
    }

    private ArthasExecuteResponse response(AppInstance instance,
                                           String requestNo,
                                           String command,
                                           boolean success,
                                           String output,
                                           String error,
                                           long start) {
        return ArthasExecuteResponse.builder()
                .requestNo(requestNo)
                .appId(instance.getAppId())
                .env(instance.getEnv())
                .command(command)
                .success(success)
                .output(output)
                .errorMessage(error)
                .costMillis(System.currentTimeMillis() - start)
                .build();
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? exception.getClass().getSimpleName() : cause.getMessage();
    }
}
