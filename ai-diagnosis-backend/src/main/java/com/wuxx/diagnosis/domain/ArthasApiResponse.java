package com.wuxx.diagnosis.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ArthasApiResponse {

    private String sessionId;

    private String consumerId;

    private String state;

    private JsonNode body;

    private JsonNode results;

    private String message;
}
