package com.plasma.be.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmOptimizationResponse(
        Current current,
        List<Candidate> candidates
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(
            @JsonProperty("process_params") ProcessParams processParams,
            @JsonProperty("prediction_result") PredictPipelineResponse.PredictionResult predictionResult,
            PlasmaDistributionResponse plasmaDistribution
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
            @JsonProperty("candidate_id")     Long candidateId,
            @JsonProperty("process_params")   ProcessParams processParams,
            @JsonProperty("prediction_result") PredictPipelineResponse.PredictionResult predictionResult,
            @JsonProperty("parameter_impact") ParameterImpact parameterImpact,
            PlasmaDistributionResponse plasmaDistribution
    ) {
    }

    public record ParameterImpact(
            List<ImpactPoint> pressure,
            @JsonProperty("source_power") List<ImpactPoint> sourcePower,
            @JsonProperty("bias_power")   List<ImpactPoint> biasPower
    ) {
    }

    public record ImpactPoint(double x, double y) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProcessParams(
            ParamValue pressure,
            @JsonProperty("source_power") ParamValue sourcePower,
            @JsonProperty("bias_power") ParamValue biasPower
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParamValue(
            Double value,
            String unit
    ) {
    }
}
