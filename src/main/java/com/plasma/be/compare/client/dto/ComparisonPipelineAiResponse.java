package com.plasma.be.compare.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;

public record ComparisonPipelineAiResponse(
        @JsonProperty("request_id")   String requestId,
        @JsonProperty("process_type") String processType,
        @JsonProperty("condition_a")  ConditionResult conditionA,
        @JsonProperty("condition_b")  ConditionResult conditionB
) {
    public record ConditionResult(
            @JsonProperty("process_params")    ProcessParams processParams,
            @JsonProperty("prediction_result") PredictPipelineResponse.PredictionResult predictionResult,
            PredictPipelineResponse.Explanation explanation
    ) {}

    public record ProcessParams(
            ValueWithUnit pressure,
            @JsonProperty("source_power") ValueWithUnit sourcePower,
            @JsonProperty("bias_power")   ValueWithUnit biasPower
    ) {}

    public record ValueWithUnit(Double value, String unit) {}
}
