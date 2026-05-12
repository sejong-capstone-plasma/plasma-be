package com.plasma.be.chat.dto;

import com.plasma.be.extract.dto.ParameterInputRequest;
import com.plasma.be.extract.dto.ParameterValidationRequest;

import java.util.ArrayList;
import java.util.List;

public record ConfirmRequest(
        String requestedTaskType,
        List<ParameterInputRequest> parameters,
        ComparisonConditionPayload conditionA,
        ComparisonConditionPayload conditionB
) {
    public boolean hasComparisonOverrides() {
        return hasParameters(parameters)
                || hasConditionParameters(normalizedConditionA())
                || hasConditionParameters(normalizedConditionB());
    }

    public ParameterValidationRequest.ComparisonConditionInput normalizedConditionA() {
        return normalizeCondition(conditionA);
    }

    public ParameterValidationRequest.ComparisonConditionInput normalizedConditionB() {
        return normalizeCondition(conditionB);
    }

    private ParameterValidationRequest.ComparisonConditionInput normalizeCondition(ComparisonConditionPayload condition) {
        if (condition == null) {
            return null;
        }

        if (hasParameters(condition.parameters())) {
            return new ParameterValidationRequest.ComparisonConditionInput(condition.parameters());
        }

        List<ParameterInputRequest> normalized = new ArrayList<>();
        addParameter(normalized, "pressure", condition.pressure());
        addParameter(normalized, "source_power", condition.source_power());
        addParameter(normalized, "bias_power", condition.bias_power());

        if (normalized.isEmpty()) {
            return null;
        }
        return new ParameterValidationRequest.ComparisonConditionInput(normalized);
    }

    private boolean hasConditionParameters(ParameterValidationRequest.ComparisonConditionInput condition) {
        return condition != null && hasParameters(condition.parameters());
    }

    private boolean hasParameters(List<ParameterInputRequest> parameters) {
        return parameters != null && !parameters.isEmpty();
    }

    private void addParameter(List<ParameterInputRequest> normalized, String key, ConditionParameterValue parameter) {
        if (parameter == null || parameter.value() == null) {
            return;
        }
        normalized.add(new ParameterInputRequest(key, parameter.value(), parameter.unit()));
    }

    public record ComparisonConditionPayload(
            List<ParameterInputRequest> parameters,
            ConditionParameterValue pressure,
            ConditionParameterValue source_power,
            ConditionParameterValue bias_power
    ) {
    }

    public record ConditionParameterValue(
            Double value,
            String unit
    ) {
    }
}
