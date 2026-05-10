package com.plasma.be.optimize.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;

import java.util.List;

public record OptimizePipelineResponse(
        @JsonProperty("request_id")          String requestId,
        @JsonProperty("process_type")        String processType,
        @JsonProperty("baseline_outputs")    BaselineOutputs baselineOutputs,
        @JsonProperty("optimization_result") OptimizationResult optimizationResult,
        Explanation explanation
) {
    public record BaselineOutputs(
            @JsonProperty("etch_score") ValueWithUnit etchScore
    ) {}

    public record OptimizationResult(
            @JsonProperty("candidate_count")         int candidateCount,
            @JsonProperty("optimization_candidates") List<OptimizationCandidate> optimizationCandidates
    ) {}

    public record OptimizationCandidate(
            int rank,
            @JsonProperty("process_params")         ProcessParams processParams,
            @JsonProperty("prediction_result")      PredictPipelineResponse.PredictionResult predictionResult,
            @JsonProperty("improvement_evaluation") ImprovementEvaluation improvementEvaluation,
            double score
    ) {}

    public record ProcessParams(
            ValueWithUnit pressure,
            @JsonProperty("source_power") ValueWithUnit sourcePower,
            @JsonProperty("bias_power")   ValueWithUnit biasPower
    ) {}

    public record ImprovementEvaluation(
            @JsonProperty("increase_value")   ValueWithUnit increaseValue,
            @JsonProperty("increase_percent") ValueWithUnit increasePercent
    ) {}

    public record ValueWithUnit(Double value, String unit) {}

    public record Explanation(String summary, List<String> details) {}
}
