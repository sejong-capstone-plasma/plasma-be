package com.plasma.be.extract.dto;

import java.util.Map;

public record ExtractionResponse(
        String requestId,
        String validationStatus,
        String processType,
        String taskType,
        Map<String, ProcessParamResponse> processParams,
        ProcessParamResponse currentEr
) {

    public record ProcessParamResponse(
            Double value,
            String unit,
            String status
    ) {}
}
