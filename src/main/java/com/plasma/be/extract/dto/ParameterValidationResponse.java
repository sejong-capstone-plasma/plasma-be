package com.plasma.be.extract.dto;

import com.plasma.be.predict.client.dto.PredictPipelineResponse;

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
        ComparisonConditionResponse conditionA,
        ComparisonConditionResponse conditionB,
        ParameterFieldResponse currentEr,
        boolean allValid,
        boolean confirmed,
        PredictPipelineResponse prediction,
        String predictionError,
        String failureReason,
        LocalDateTime createdAt
) {
    public record ComparisonConditionResponse(
            String label,
            List<ParameterFieldResponse> parameters
    ) {
    }
}
