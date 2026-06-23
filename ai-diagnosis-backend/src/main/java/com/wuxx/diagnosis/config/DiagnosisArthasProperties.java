package com.wuxx.diagnosis.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "diagnosis.arthas")
public class DiagnosisArthasProperties {

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 120000;

    private int execTimeoutMs = 30000;

    private int traceExecTimeoutMs = 90000;

    private int maxOutputLength = 20000;

    private int auditOutputExcerptLength = 4000;

    /**
     * 开发/测试诊断沙箱开关。开启后 AI 可以提交原生 Arthas 命令。
     */
    private boolean unrestrictedAiCommandsEnabled = false;

    private List<String> unrestrictedAiEnvironments = new ArrayList<>(List.of("dev", "test"));

    private int unrestrictedAiMaxCommandLength = 4000;

    private int unrestrictedAiMaxCallsPerTask = 12;

    private int unrestrictedAiExecTimeoutMs = 90000;

    private Tunnel tunnel = new Tunnel();

    @Getter
    @Setter
    public static class Tunnel {

        private String wsUrl = "ws://127.0.0.1:7777/ws";

        private String webUrl = "http://127.0.0.1:8080";
    }
}
