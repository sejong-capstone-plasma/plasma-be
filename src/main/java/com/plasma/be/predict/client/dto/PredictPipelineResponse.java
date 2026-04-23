package com.plasma.be.predict.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PredictPipelineResponse(
        @JsonProperty("request_id")       String requestId,
        @JsonProperty("process_type")     String processType,
        @JsonProperty("prediction_result") PredictionResult predictionResult,
        Explanation explanation
) {

    public record PredictionResult(
            @JsonProperty("ion_flux")   ValueWithUnit ionFlux,
            @JsonProperty("ion_energy") ValueWithUnit ionEnergy,
            @JsonProperty("etch_score") ValueWithUnit etchScore
    ) {}

    public record ValueWithUnit(Double value, String unit) {}

    public record Explanation(String summary, List<String> details) {}
}
