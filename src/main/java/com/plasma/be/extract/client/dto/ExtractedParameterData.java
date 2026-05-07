package com.plasma.be.extract.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExtractedParameterData(
        @JsonProperty("request_id")      String requestId,
        @JsonProperty("validation_status") String validationStatus,
        @JsonProperty("process_type")    String processType,
        @JsonProperty("task_type")       String taskType,
        @JsonProperty("process_params")  ProcessParams processParams,
        @JsonProperty("current_outputs") CurrentOutputs currentOutputs,
        @JsonProperty("condition_a")     ProcessParams conditionA,
        @JsonProperty("condition_b")     ProcessParams conditionB
) {

    public record ProcessParams(
            ValidatedParam pressure,
            @JsonProperty("source_power") ValidatedParam sourcePower,
            @JsonProperty("bias_power")   ValidatedParam biasPower
    ) {}

    public record ValidatedParam(
            Double value,
            String unit,
            String status
    ) {}

    public record ValueWithUnit(
            Double value,
            String unit
    ) {}

    public record CurrentOutputs(
            @JsonProperty("etch_rate") ValueWithUnit etchRate
    ) {}
}
