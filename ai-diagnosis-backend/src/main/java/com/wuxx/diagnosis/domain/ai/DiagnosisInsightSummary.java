package com.wuxx.diagnosis.domain.ai;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DiagnosisInsightSummary {

    private String rootCause;

    private List<String> specificReasons = new ArrayList<>();

    private String expectedEffect;

    private List<String> recommendedActions = new ArrayList<>();
}
