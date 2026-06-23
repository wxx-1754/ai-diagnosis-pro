package com.wuxx.diagnosis.arthas;

import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
public class ArthasRoutingCommandExecutor implements ArthasCommandExecutor {

    private final ArthasHttpCommandExecutor httpCommandExecutor;
    private final ArthasTunnelCommandExecutor tunnelCommandExecutor;

    @Override
    public ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType) {
        String accessMode = instance.getAccessMode();
        if (accessMode == null || "HTTP".equalsIgnoreCase(accessMode)) {
            return httpCommandExecutor.execute(instance, requestNo, command, commandType);
        }
        if ("TUNNEL".equalsIgnoreCase(accessMode)) {
            return tunnelCommandExecutor.execute(instance, requestNo, command, commandType);
        }
        throw new IllegalArgumentException("Unsupported Arthas accessMode: " + accessMode);
    }
}
