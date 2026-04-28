package com.plasma.be.optimize.client.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

public class OptimizePipelineResponse {

    private final Map<String, Object> payload = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        payload.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> payload() {
        return payload;
    }
}
