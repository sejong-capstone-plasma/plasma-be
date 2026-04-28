package com.plasma.be.optimize.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record OptimizeRequest(
        String originalUserInput,
        String processType,
        JsonNode processParams,
        JsonNode currentOutputs,
        JsonNode targetOutputs
) {
}
