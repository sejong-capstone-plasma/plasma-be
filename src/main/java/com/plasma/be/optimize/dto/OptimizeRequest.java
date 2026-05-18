package com.plasma.be.optimize.dto;

import java.util.Map;

public record OptimizeRequest(
        String originalUserInput,
        String processType,
        Map<String, Object> processParams
) {
}
