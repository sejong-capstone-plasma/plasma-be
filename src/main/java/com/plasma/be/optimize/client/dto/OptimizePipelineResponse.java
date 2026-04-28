package com.plasma.be.optimize.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record OptimizePipelineResponse(Object payload) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public OptimizePipelineResponse {
    }

    @JsonValue
    public Object payload() {
        return payload;
    }
}
