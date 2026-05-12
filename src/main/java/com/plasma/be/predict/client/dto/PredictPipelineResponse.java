package com.plasma.be.predict.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PredictPipelineResponse(
        @JsonProperty("request_id")        String requestId,
        @JsonProperty("process_type")      String processType,
        @JsonProperty("prediction_result") PredictionResult predictionResult,
        Explanation explanation,
        Graphs graphs
) {

    public record PredictionResult(
            @JsonProperty("ion_flux")   ValueWithUnit ionFlux,
            @JsonProperty("ion_energy") ValueWithUnit ionEnergy,
            @JsonProperty("etch_score") ValueWithUnit etchScore
    ) {}

    public record ValueWithUnit(Double value, String unit) {}

    public record Explanation(String summary, List<String> details) {}

    public record Graphs(
            List<XYPoint> cur,
            List<XYPoint> iad,
            List<XYPoint> ied
    ) {}

    public record XYPoint(double x, double y) {}

    public PredictPipelineResponse withGraphs(Graphs graphs) {
        return new PredictPipelineResponse(requestId, processType, predictionResult, explanation, graphs);
    }
}
