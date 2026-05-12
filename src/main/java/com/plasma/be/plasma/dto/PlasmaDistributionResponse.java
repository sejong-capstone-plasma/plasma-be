package com.plasma.be.plasma.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlasmaDistributionResponse(
        @JsonProperty("matched_pressure")    double matchedPressure,
        @JsonProperty("matched_source_power") double matchedSourcePower,
        @JsonProperty("matched_bias_power")  double matchedBiasPower,
        @JsonProperty("ion_flux")            Double ionFlux,
        @JsonProperty("avg_energy")          Double avgEnergy,
        @JsonProperty("ied_energy_min")      Double iedEnergyMin,
        @JsonProperty("ied_x_values")        List<Double> iedXValues,
        @JsonProperty("ied_y_values")        List<Double> iedYValues,
        @JsonProperty("iad_x_values")        List<Double> iadXValues,
        @JsonProperty("iad_y_values")        List<Double> iadYValues,
        @JsonProperty("cur_x_values")        List<Double> curXValues,
        @JsonProperty("cur_y_values")        List<Double> curYValues
) {
}
