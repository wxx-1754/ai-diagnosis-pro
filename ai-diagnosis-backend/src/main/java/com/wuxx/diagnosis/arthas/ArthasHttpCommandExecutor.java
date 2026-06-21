package com.wuxx.diagnosis.arthas;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasApiResponse;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArthasHttpCommandExecutor implements ArthasCommandExecutor {

    private final RestClient restClient;

    private final ArthasCommandGuard commandGuard;

    private final DiagnosisArthasProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType) {
        long start = System.currentTimeMillis();
        try {
            // 双保险：即使命令来自工厂，真正出网前仍要过白名单，防止后续调用方绕过映射层。
            if ("rawArthas".equals(commandType)) {
                checkUnrestrictedAccess(instance, command);
            } else {
                commandGuard.check(command);
            }
            String apiUrl = buildApiUrl(instance);
            log.info("Calling Arthas HTTP API, requestNo={}, url={}, commandType={}",
                    requestNo, apiUrl, commandType);

            byte[] responseBody = restClient.post()
                    .uri(apiUrl)
                    .headers(headers -> applyHeaders(headers, instance))
                    .body(buildRequestBody(command, requestNo))
                    .exchange((request, response) -> response.getBody().readAllBytes());

            ArthasApiResponse apiResponse = parseResponse(responseBody);
            validateState(apiResponse, command);
            String output = truncate(extractOutput(apiResponse), properties.getMaxOutputLength());

            return ArthasExecuteResponse.builder()
                    .requestNo(requestNo)
                    .appId(instance.getAppId())
                    .env(instance.getEnv())
                    .command(command)
                    .success(true)
                    .output(output)
                    .costMillis(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception exception) {
            // 目标应用未 attach Arthas、端口不通、命令失败都作为本次诊断失败返回给上层。
            log.warn("Arthas HTTP API call failed, requestNo={}, appId={}, env={}, commandType={}, message={}",
                    requestNo, instance.getAppId(), instance.getEnv(), commandType, exception.getMessage());
            log.debug("Arthas HTTP API failure detail, requestNo={}", requestNo, exception);
            return ArthasExecuteResponse.builder()
                    .requestNo(requestNo)
                    .appId(instance.getAppId())
                    .env(instance.getEnv())
                    .command(command)
                    .success(false)
                    .errorMessage(exception.getMessage())
                    .costMillis(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private Map<String, Object> buildRequestBody(String command, String requestNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", "exec");
        body.put("requestId", requestNo);
        body.put("command", command);
        body.put("execTimeout", String.valueOf(resolveExecTimeout(command)));
        return body;
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

    private void checkUnrestrictedAccess(AppInstance instance, String command) {
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

    private String buildApiUrl(AppInstance instance) {
        return "http://" + instance.getIp() + ":" + instance.getArthasHttpPort() + "/api";
    }

    private void applyHeaders(HttpHeaders headers, AppInstance instance) {
        headers.setAccept(MediaType.parseMediaTypes("application/json, application/octet-stream, */*"));
        // Arthas HTTP 认证是实例级配置；未配置用户名时保持无认证请求，兼容本地默认启动方式。
        if (!StringUtils.hasText(instance.getArthasUsername())) {
            return;
        }
        String credentials = instance.getArthasUsername() + ":" + nullToEmpty(instance.getArthasPassword());
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
    }

    private ArthasApiResponse parseResponse(byte[] responseBody) throws Exception {
        if (responseBody == null || responseBody.length == 0) {
            throw new IllegalStateException("Empty Arthas response");
        }

        String text = new String(responseBody, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("Empty Arthas response");
        }
        return objectMapper.readValue(text, ArthasApiResponse.class);
    }

    private void validateState(ArthasApiResponse response, String command) {
        if (response == null) {
            throw new IllegalStateException("Empty Arthas response");
        }
        if (StringUtils.hasText(response.getState()) && !"SUCCEEDED".equalsIgnoreCase(response.getState())) {
            String message = StringUtils.hasText(response.getMessage())
                    ? response.getMessage()
                    : "Arthas command state is " + response.getState();
            throw new IllegalStateException(message);
        }
        JsonNode body = response.getBody();
        if (body != null && body.path("timeExpired").asBoolean(false)) {
            if (command != null && command.trim().startsWith("trace ")) {
                throw new IllegalStateException("Trace 在 " + properties.getTraceExecTimeoutMs()
                        + "ms 内未命中目标方法；请在采样期间实际请求目标接口后重试");
            }
            throw new IllegalStateException("Arthas 命令执行超过 "
                    + properties.getExecTimeoutMs() + "ms，已自动终止");
        }
    }

    private String extractOutput(ArthasApiResponse response) {
        // 不同 Arthas 命令/版本可能把文本放在 body、results 或 message，这里统一抽成字符串给前端和 AI Tool。
        if (response == null) {
            return "";
        }
        JsonNode body = response.getBody();
        if (body != null && !body.isNull()) {
            return body.isTextual() ? body.asText() : body.toPrettyString();
        }
        JsonNode results = response.getResults();
        if (results != null && !results.isNull()) {
            return results.toPrettyString();
        }
        if (StringUtils.hasText(response.getMessage())) {
            return response.getMessage();
        }
        return "";
    }

    private String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
