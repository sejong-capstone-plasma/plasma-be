package com.plasma.be.chat.dto;

import com.plasma.be.extract.dto.ParameterInputRequest;
import com.plasma.be.extract.dto.ParameterValidationRequest;

import java.util.List;

public record ConfirmRequest(
        String requestedTaskType,
        List<ParameterInputRequest> parameters,
        ParameterValidationRequest.ComparisonConditionInput conditionA,
        ParameterValidationRequest.ComparisonConditionInput conditionB
) {
    public boolean hasComparisonOverrides() {
        return hasParameters(parameters)
                || hasConditionParameters(conditionA)
                || hasConditionParameters(conditionB);
    }

    private boolean hasConditionParameters(ParameterValidationRequest.ComparisonConditionInput condition) {
        return condition != null && hasParameters(condition.parameters());
    }

    private boolean hasParameters(List<ParameterInputRequest> parameters) {
        return parameters != null && !parameters.isEmpty();
    }
}
