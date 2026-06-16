package com.wuxx.diagnosis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.wuxx.diagnosis.domain.ArthasExecuteRequest;
import com.wuxx.diagnosis.domain.ArthasExecuteResponse;
import com.wuxx.diagnosis.service.ArthasCommandService;
import org.junit.jupiter.api.Test;

class ArthasCommandControllerTest {

    @Test
    void healthDelegatesToJvmCommand() {
        CapturingArthasCommandService service = new CapturingArthasCommandService();
        ArthasCommandController controller = new ArthasCommandController(service);

        ArthasExecuteResponse response = controller.health("order-service", "test");

        assertThat(response.isSuccess()).isTrue();
        assertThat(service.request.getAppId()).isEqualTo("order-service");
        assertThat(service.request.getEnv()).isEqualTo("test");
        assertThat(service.request.getCommandType()).isEqualTo("jvm");
    }

    private static class CapturingArthasCommandService extends ArthasCommandService {

        private ArthasExecuteRequest request;

        CapturingArthasCommandService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ArthasExecuteResponse execute(ArthasExecuteRequest request) {
            this.request = request;
            return ArthasExecuteResponse.builder()
                    .requestNo("REQ-1")
                    .appId(request.getAppId())
                    .env(request.getEnv())
                    .command("jvm")
                    .success(true)
                    .output("ok")
                    .costMillis(1)
                    .build();
        }
    }
}
