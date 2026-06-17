package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.wuxx.diagnosis.config.AiConfig;
import com.wuxx.diagnosis.controller.AiDiagnoseController;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class AiBeanConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    AiConfig.class,
                    AiDiagnoseController.class,
                    DiagnoseIntentClassifier.class,
                    DiagnosisReportGenerator.class,
                    AiDiagnosisOrchestrator.class
            );

    @Test
    void aiBeansAreNotCreatedWhenAiDiagnosisIsDisabled() {
        contextRunner
                .withPropertyValues("diagnosis.ai.enable=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatClient.class);
                    assertThat(context).doesNotHaveBean(AiDiagnoseController.class);
                    assertThat(context).doesNotHaveBean(DiagnoseIntentClassifier.class);
                    assertThat(context).doesNotHaveBean(DiagnosisReportGenerator.class);
                    assertThat(context).doesNotHaveBean(AiDiagnosisOrchestrator.class);
                });
    }

    @Test
    void aiConfigCreatesChatClientWhenAiDiagnosisIsEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiConfig.class, StubChatClientBuilderConfiguration.class)
                .withPropertyValues("diagnosis.ai.enable=true")
                .run(context -> assertThat(context).hasSingleBean(ChatClient.class));
    }

    static class StubChatClientBuilderConfiguration {

        @Bean
        ChatClient.Builder chatClientBuilder() {
            return ChatClient.builder(new StubChatModel());
        }
    }

    static class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("Stub model should not be called");
        }
    }
}
