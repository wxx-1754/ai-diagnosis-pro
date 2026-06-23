package com.wuxx.diagnosis.arthas;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ArthasCommandGuard {

    private static final Set<String> ALLOWED_COMMAND_PREFIXES = Set.of(
            "dashboard",
            "thread",
            "jvm",
            "memory",
            "trace",
            "version"
    );

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "dashboard -n 1",
            "thread",
            "thread -b",
            "jvm",
            "memory",
            "version"
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
            "watch"
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

        if (normalized.startsWith("trace")) {
            checkTraceCommand(normalized);
            return;
        }

        if ("thread".equals(normalized) || "thread -b".equals(normalized)) {
            return;
        }

        if (normalized.startsWith("thread -n")) {
            checkThreadTopCommand(normalized);
            return;
        }

        if (normalized.startsWith("thread ")) {
            checkThreadStackCommand(normalized);
            return;
        }

        if (!ALLOWED_COMMANDS.contains(normalized)) {
            throw new SecurityException("Unsupported Arthas command detail: " + normalized);
        }
    }

    public void checkUnrestricted(String command, int maxLength) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Arthas command cannot be empty");
        }
        String normalized = command.trim();
        if (normalized.length() > maxLength) {
            throw new SecurityException("Arthas command length exceeds limit: " + maxLength);
        }
        if (normalized.chars().anyMatch(value -> value == 0 || value == '\r' || value == '\n')) {
            throw new SecurityException("Arthas command contains invalid control characters");
        }
    }

    private void checkThreadTopCommand(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 3 || !"thread".equals(parts[0]) || !"-n".equals(parts[1])) {
            throw new SecurityException("Only thread -n {1~10} is allowed");
        }
        int topN = parsePositiveInt(parts[2], "Invalid thread topN: " + parts[2]);
        if (topN < 1 || topN > 10) {
            throw new SecurityException("thread -n only allows 1~10");
        }
    }

    private void checkThreadStackCommand(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 2 || !"thread".equals(parts[0])) {
            throw new SecurityException("Only thread {threadId} is allowed");
        }
        long threadId = parsePositiveLong(parts[1], "Invalid threadId: " + parts[1]);
        if (threadId <= 0) {
            throw new SecurityException("threadId must be positive");
        }
    }

    private void checkTraceCommand(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 5) {
            throw new SecurityException("Only trace {className} {methodName} -n 3 is allowed");
        }
        if (!"trace".equals(parts[0])) {
            throw new SecurityException("Invalid trace command");
        }
        if (!parts[1].matches("[a-zA-Z_$][a-zA-Z\\d_$]*(\\.[a-zA-Z_$][a-zA-Z\\d_$]*)*")) {
            throw new SecurityException("Invalid trace className: " + parts[1]);
        }
        if (!parts[2].matches("[a-zA-Z_$][a-zA-Z\\d_$]*")) {
            throw new SecurityException("Invalid trace methodName: " + parts[2]);
        }
        if (!"-n".equals(parts[3]) || !"3".equals(parts[4])) {
            throw new SecurityException("Only trace -n 3 is allowed");
        }
    }

    private int parsePositiveInt(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new SecurityException(message);
        }
    }

    private long parsePositiveLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new SecurityException(message);
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
