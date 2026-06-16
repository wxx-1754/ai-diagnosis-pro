package com.wuxx.diagnosis.arthas;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasApiResponse;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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

    @Override
    public ArthasExecuteResponse execute(AppInstance instance, String requestNo, String command, String commandType) {
        long start = System.currentTimeMillis();
        try {
            // 双保险：即使命令来自工厂，真正出网前仍要过白名单，防止后续调用方绕过映射层。
            commandGuard.check(command);
            String apiUrl = buildApiUrl(instance);
            log.info("Calling Arthas HTTP API, requestNo={}, url={}, commandType={}",
                    requestNo, apiUrl, commandType);

            ArthasApiResponse apiResponse = restClient.post()
                    .uri(apiUrl)
                    .headers(headers -> applyBasicAuth(headers, instance))
                    .body(buildRequestBody(command))
                    .retrieve()
                    .body(ArthasApiResponse.class);

            validateState(apiResponse);
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

    private Map<String, Object> buildRequestBody(String command) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", "exec");
        body.put("command", command);
        return body;
    }

    private String buildApiUrl(AppInstance instance) {
        return "http://" + instance.getIp() + ":" + instance.getArthasHttpPort() + "/api";
    }

    private void applyBasicAuth(HttpHeaders headers, AppInstance instance) {
        // Arthas HTTP 认证是实例级配置；未配置用户名时保持无认证请求，兼容本地默认启动方式。
        if (!StringUtils.hasText(instance.getArthasUsername())) {
            return;
        }
        String credentials = instance.getArthasUsername() + ":" + nullToEmpty(instance.getArthasPassword());
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
    }

    private void validateState(ArthasApiResponse response) {
        if (response == null) {
            throw new IllegalStateException("Empty Arthas response");
        }
        if (StringUtils.hasText(response.getState()) && !"SUCCEEDED".equalsIgnoreCase(response.getState())) {
            String message = StringUtils.hasText(response.getMessage())
                    ? response.getMessage()
                    : "Arthas command state is " + response.getState();
            throw new IllegalStateException(message);
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
