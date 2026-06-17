package com.wuxx.diagnosis.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuxx.diagnosis.config.DiagnosisAiProperties;
import com.wuxx.diagnosis.domain.DiagnoseType;
import com.wuxx.diagnosis.domain.ai.DiagnoseIntentResult;
import org.junit.jupiter.api.Test;

class DiagnoseIntentClassifierTest {

    private final DiagnoseIntentClassifier classifier = new DiagnoseIntentClassifier(
            null,
            new ObjectMapper(),
            new DiagnosisAiProperties()
    );

    @Test
    void parseAndValidateAcceptsJsonFenceAndNormalizesType() {
        DiagnoseIntentResult result = classifier.parseAndValidate("""
                ```json
                {
                  "diagnoseType": "high_cpu",
                  "confidence": 1.3,
                  "reason": "CPU high",
                  "targetClass": " ",
                  "targetMethod": "createOrder"
                }
                ```
                """);

        assertThat(result.getDiagnoseType()).isEqualTo(DiagnoseType.HIGH_CPU.name());
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getTargetClass()).isNull();
        assertThat(result.getTargetMethod()).isEqualTo("createOrder");
    }

    @Test
    void parseAndValidateFallsBackToUnknownForInvalidJson() {
        DiagnoseIntentResult result = classifier.parseAndValidate("not-json");

        assertThat(result.getDiagnoseType()).isEqualTo(DiagnoseType.UNKNOWN.name());
        assertThat(result.getConfidence()).isZero();
        assertThat(result.getReason()).contains("AI 返回解析失败");
    }

    @Test
    void parseAndValidateRejectsUnsupportedType() {
        DiagnoseIntentResult result = classifier.parseAndValidate("""
                {"diagnoseType":"HEAP_DUMP","confidence":0.9,"reason":"bad"}
                """);

        assertThat(result.getDiagnoseType()).isEqualTo(DiagnoseType.UNKNOWN.name());
        assertThat(result.getConfidence()).isEqualTo(0.9);
    }
}
