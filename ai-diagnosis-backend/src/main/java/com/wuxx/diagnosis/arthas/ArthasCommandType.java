package com.wuxx.diagnosis.arthas;

import lombok.Getter;

@Getter
public enum ArthasCommandType {

    DASHBOARD("dashboard", "dashboard -n 1"),
    THREAD("thread", "thread"),
    TOP_THREAD("topThread", "thread -n 5"),
    THREAD_BLOCK("threadBlock", "thread -b"),
    JVM("jvm", "jvm"),
    MEMORY("memory", "memory");

    private final String code;

    private final String command;

    ArthasCommandType(String code, String command) {
        this.code = code;
        this.command = command;
    }

    public static ArthasCommandType fromCode(String code) {
        String normalizedCode = code == null ? "" : code.trim();
        for (ArthasCommandType type : values()) {
            if (type.code.equalsIgnoreCase(normalizedCode)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported commandType: " + code);
    }
}
