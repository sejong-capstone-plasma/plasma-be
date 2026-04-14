package com.plasma.be.plasma.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlasmaExtractResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("validation_status") String validationStatus,
        @JsonProperty("process_type") String processType,
        @JsonProperty("task_type") String taskType,
        @JsonProperty("process_params") ProcessParams processParams,
        @JsonProperty("current_outputs") CurrentOutputs currentOutputs
) {

    public record ProcessParams(
            ValidatedValueWithUnit pressure,
            @JsonProperty("source_power") ValidatedValueWithUnit sourcePower,
            @JsonProperty("bias_power") ValidatedValueWithUnit biasPower
    ) {}

    public record ValidatedValueWithUnit(
            Double value,
            String unit,
            String status
    ) {}

    public record CurrentOutputs(
            @JsonProperty("etch_rate") ValueWithUnit etchRate
    ) {}

    public record ValueWithUnit(
            Double value,
            String unit
    ) {}
}
