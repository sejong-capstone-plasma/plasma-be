package com.plasma.be.extract.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ParameterValidationResponse(
        Long validationId,
        String requestId,
        Long messageId,
        int attemptNo,
        String sourceType,
        String validationStatus,
        String processType,
        String taskType,
        List<ParameterFieldResponse> parameters,
        ParameterFieldResponse currentEr,
        boolean allValid,
        boolean confirmed,
        String failureReason,
        LocalDateTime createdAt
) {
}
