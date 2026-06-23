package com.wuxx.diagnosis.arthas;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class JdkArthasTunnelClient implements ArthasTunnelClient {

    private static final Pattern ANSI_PATTERN = Pattern.compile(
            "\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))");

    private static final Pattern PROMPT_PATTERN = Pattern.compile(
            "(?m)\\[arthas@[^\\]\\r\\n]+]\\$\\s*$");

    private final DiagnosisArthasProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public JdkArthasTunnelClient(DiagnosisArthasProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    @Override
    public String execute(String agentId, String command, int timeoutMillis) throws Exception {
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("arthasAgentId 不能为空");
        }
        URI uri = buildUri(properties.getTunnel().getWsUrl(), agentId);
        TunnelListener listener = new TunnelListener(command, objectMapper, properties.getMaxOutputLength());
        WebSocket webSocket = null;
        try {
            log.info("Connecting Arthas Tunnel WebSocket, agentId={}, uri={}", agentId, uri);
            webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .buildAsync(uri, listener)
                    .get(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
            listener.bind(webSocket);
            return listener.result().get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            if (webSocket != null) {
                sendRead(webSocket, "\u0003");
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "command timeout");
            }
            throw new IllegalStateException("Arthas Tunnel 命令执行超过 " + timeoutMillis + "ms，已自动中断");
        } finally {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "command completed");
            }
        }
    }

    static URI buildUri(String wsUrl, String agentId) {
        if (!StringUtils.hasText(wsUrl)) {
            throw new IllegalStateException("diagnosis.arthas.tunnel.ws-url 未配置");
        }
        String separator = wsUrl.contains("?") ? "&" : "?";
        String encodedAgentId = URLEncoder.encode(agentId, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(wsUrl + separator + "method=connectArthas&id=" + encodedAgentId);
    }

    static String cleanCommandOutput(String rawOutput, String command) {
        String text = stripAnsi(rawOutput).replace("\r", "");
        text = text.replaceFirst("^\\s*" + Pattern.quote(command) + "\\s*\\n", "");
        text = PROMPT_PATTERN.matcher(text).replaceFirst("");
        return text.strip();
    }

    private static String stripAnsi(String text) {
        return text == null ? "" : ANSI_PATTERN.matcher(text).replaceAll("");
    }

    private static CompletableFuture<WebSocket> sendRead(WebSocket webSocket, String data) {
        try {
            String payload = new ObjectMapper().writeValueAsString(Map.of("action", "read", "data", data));
            return webSocket.sendText(payload, true);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static final class TunnelListener implements WebSocket.Listener {

        private static final int PROMPT_TAIL_LENGTH = 1024;

        private final String command;
        private final ObjectMapper objectMapper;
        private final int outputLimit;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final StringBuilder frameBuffer = new StringBuilder();
        private final StringBuilder output = new StringBuilder();
        private final StringBuilder promptTail = new StringBuilder();
        private volatile WebSocket webSocket;
        private volatile boolean commandSent;

        private TunnelListener(String command, ObjectMapper objectMapper, int outputLimit) {
            this.command = command;
            this.objectMapper = objectMapper;
            this.outputLimit = Math.max(outputLimit, 1);
        }

        void bind(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        CompletableFuture<String> result() {
            return result;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            bind(webSocket);
            webSocket.request(1);
            send(webSocket, Map.of("action", "resize", "cols", 160, "rows", 40));
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frameBuffer.append(data);
            if (last) {
                processFrame(webSocket, frameBuffer.toString());
                frameBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return webSocket.sendPong(message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!result.isDone()) {
                result.completeExceptionally(new IllegalStateException(
                        "Arthas Tunnel WebSocket 已关闭，code=" + statusCode + ", reason=" + reason));
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            result.completeExceptionally(error);
        }

        private void processFrame(WebSocket webSocket, String frame) {
            String cleanFrame = stripAnsi(frame).replace("\r", "");
            if (!commandSent) {
                appendPromptTail(cleanFrame);
                if (PROMPT_PATTERN.matcher(promptTail).find()) {
                    commandSent = true;
                    promptTail.setLength(0);
                    send(webSocket, Map.of("action", "read", "data", command + "\r"));
                }
                return;
            }

            appendOutput(cleanFrame);
            appendPromptTail(cleanFrame);
            if (PROMPT_PATTERN.matcher(promptTail).find() && result.complete(cleanCommandOutput(output.toString(), command))) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "command completed");
            }
        }

        private void appendOutput(String text) {
            int remaining = outputLimit - output.length();
            if (remaining > 0) {
                output.append(text, 0, Math.min(remaining, text.length()));
            }
        }

        private void appendPromptTail(String text) {
            promptTail.append(text);
            if (promptTail.length() > PROMPT_TAIL_LENGTH) {
                promptTail.delete(0, promptTail.length() - PROMPT_TAIL_LENGTH);
            }
        }

        private void send(WebSocket webSocket, Map<String, Object> payload) {
            try {
                webSocket.sendText(objectMapper.writeValueAsString(payload), true);
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }
    }
}
