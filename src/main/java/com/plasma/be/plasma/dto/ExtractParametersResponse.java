package com.plasma.be.plasma.dto;

public record ExtractParametersResponse(
        String requestId,
        String validationStatus,
        String processType,
        String taskType,
        ProcessParams processParams,
        CurrentOutputs currentOutputs
) {

    public record ProcessParams(
            ValidatedValueWithUnit pressure,
            ValidatedValueWithUnit sourcePower,
            ValidatedValueWithUnit biasPower
    ) {}

    public record ValidatedValueWithUnit(
            Double value,
            String unit,
            String status
    ) {}

    public record CurrentOutputs(
            ValueWithUnit etchRate
    ) {}

    public record ValueWithUnit(
            Double value,
            String unit
    ) {}
}
