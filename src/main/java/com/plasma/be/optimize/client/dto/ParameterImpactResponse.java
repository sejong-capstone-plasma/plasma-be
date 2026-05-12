package com.plasma.be.optimize.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ParameterImpactResponse(
        @JsonProperty("request_id")          String requestId,
        @JsonProperty("process_type")        String processType,
        @JsonProperty("pressure_impact")     List<ImpactPoint> pressureImpact,
        @JsonProperty("source_power_impact") List<ImpactPoint> sourcePowerImpact,
        @JsonProperty("bias_power_impact")   List<ImpactPoint> biasPowerImpact
) {
    public record ImpactPoint(double x, double y) {}
}
