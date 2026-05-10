package com.plasma.be.extract.dto;

import java.util.List;

public record ParameterValidationRequest(
        List<ParameterInputRequest> parameters,
        ComparisonConditionInput conditionA,
        ComparisonConditionInput conditionB
) {
    public ParameterValidationRequest(List<ParameterInputRequest> parameters) {
        this(parameters, null, null);
    }

    public record ComparisonConditionInput(
            List<ParameterInputRequest> parameters
    ) {
    }
}
