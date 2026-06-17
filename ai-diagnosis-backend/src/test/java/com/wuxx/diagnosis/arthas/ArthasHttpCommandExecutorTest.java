package com.wuxx.diagnosis.arthas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisArthasProperties;
import com.wuxx.diagnosis.domain.AppInstance;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ArthasHttpCommandExecutorTest {

    @Test
    void executePostsFixedCommandToArthasApi() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ArthasHttpCommandExecutor executor = new ArthasHttpCommandExecutor(
                restClientBuilder.build(),
                new ArthasCommandGuard(),
                properties(),
                new ObjectMapper()
        );

        server.expect(once(), requestTo("http://127.0.0.1:8563/api"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("arthas", "secret")))
                .andExpect(jsonPath("$.action").value("exec"))
                .andExpect(jsonPath("$.command").value("jvm"))
                .andRespond(withSuccess("""
                        {
                          "state": "SUCCEEDED",
                          "body": {
                            "results": [
                              {
                                "type": "jvm",
                                "content": "JVM output"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ArthasExecuteResponse response = executor.execute(instance(), "REQ-1", "jvm", "jvm");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestNo()).isEqualTo("REQ-1");
        assertThat(response.getOutput()).contains("JVM output");
        server.verify();
    }

    @Test
    void executeParsesJsonResponseWhenArthasReturnsOctetStream() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ArthasHttpCommandExecutor executor = new ArthasHttpCommandExecutor(
                restClientBuilder.build(),
                new ArthasCommandGuard(),
                properties(),
                new ObjectMapper()
        );

        server.expect(once(), requestTo("http://127.0.0.1:8563/api"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.command").value("trace com.example.OrderController createOrder -n 3"))
                .andRespond(withSuccess("""
                        {
                          "state": "SUCCEEDED",
                          "body": {
                            "results": [
                              {
                                "type": "trace",
                                "content": "trace output"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_OCTET_STREAM));

        ArthasExecuteResponse response = executor.execute(
                instance(),
                "REQ-TRACE",
                "trace com.example.OrderController createOrder -n 3",
                "trace"
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutput()).contains("trace output");
        server.verify();
    }

    @Test
    void executeReturnsFailureWhenArthasStateFails() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ArthasHttpCommandExecutor executor = new ArthasHttpCommandExecutor(
                restClientBuilder.build(),
                new ArthasCommandGuard(),
                properties(),
                new ObjectMapper()
        );

        server.expect(once(), requestTo("http://127.0.0.1:8563/api"))
                .andRespond(withSuccess("""
                        {
                          "state": "FAILED",
                          "message": "command failed"
                        }
                        """, MediaType.APPLICATION_JSON));

        ArthasExecuteResponse response = executor.execute(instance(), "REQ-2", "jvm", "jvm");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("command failed");
        server.verify();
    }

    private AppInstance instance() {
        AppInstance instance = new AppInstance();
        instance.setAppId("order-service");
        instance.setEnv("test");
        instance.setIp("127.0.0.1");
        instance.setArthasHttpPort(8563);
        instance.setArthasUsername("arthas");
        instance.setArthasPassword("secret");
        return instance;
    }

    private DiagnosisArthasProperties properties() {
        DiagnosisArthasProperties properties = new DiagnosisArthasProperties();
        properties.setMaxOutputLength(20000);
        return properties;
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedCredentials;
    }
}
