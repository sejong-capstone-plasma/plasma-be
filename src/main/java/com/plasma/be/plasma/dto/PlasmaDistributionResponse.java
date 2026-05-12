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
        @JsonProperty("ied_values")          List<Double> iedValues,
        @JsonProperty("iad_values")          List<Double> iadValues,
        @JsonProperty("cur_values")          List<Double> curValues
) {
}
