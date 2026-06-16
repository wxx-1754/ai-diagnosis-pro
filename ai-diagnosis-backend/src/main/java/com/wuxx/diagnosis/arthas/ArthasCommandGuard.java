package com.wuxx.diagnosis.arthas;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ArthasCommandGuard {

    private static final Set<String> ALLOWED_COMMAND_PREFIXES = Set.of(
            "dashboard",
            "thread",
            "jvm",
            "memory"
    );

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "dashboard -n 1",
            "thread",
            "thread -n 5",
            "jvm",
            "memory"
    );

    private static final Set<String> FORBIDDEN_COMMAND_PREFIXES = Set.of(
            "heapdump",
            "ognl",
            "dump",
            "jad",
            "redefine",
            "retransform",
            "stop",
            "shutdown",
            "logger",
            "watch",
            "trace"
    );

    public void check(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Arthas command cannot be empty");
        }

        String normalized = command.trim();
        // Arthas HTTP API 不经过 shell，这里仍拦截 shell 元字符，避免未来替换执行通道时引入命令注入风险。
        if (containsShellMeta(normalized)) {
            throw new SecurityException("Command injection risk detected: " + normalized);
        }

        String prefix = normalized.split("\\s+")[0];
        if (FORBIDDEN_COMMAND_PREFIXES.contains(prefix)) {
            throw new SecurityException("Forbidden Arthas command: " + prefix);
        }
        if (!ALLOWED_COMMAND_PREFIXES.contains(prefix)) {
            throw new SecurityException("Unsupported Arthas command: " + prefix);
        }

        // 第一阶段只允许固定命令全集，不能放开 thread/dashboard 的任意参数。
        if (!ALLOWED_COMMANDS.contains(normalized)) {
            throw new SecurityException("Unsupported Arthas command detail: " + normalized);
        }
    }

    private boolean containsShellMeta(String command) {
        return command.contains(";")
                || command.contains("&&")
                || command.contains("||")
                || command.contains("|")
                || command.contains("`")
                || command.contains("$(")
                || command.contains(">");
    }
}
