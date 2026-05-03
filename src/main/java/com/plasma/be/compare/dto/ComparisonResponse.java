package com.plasma.be.compare.dto;

import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;

import java.util.List;

public record ComparisonResponse(
        ConditionResult left,
        ConditionResult right,
        Difference difference,
        String summary
) {
    public record ConditionResult(
            String label,
            String processType,
            List<ParameterFieldResponse> parameters,
            PredictPipelineResponse prediction
    ) {
    }

    public record Difference(
            Double ionFluxDelta,
            String ionFluxUnit,
            Double ionEnergyDelta,
            String ionEnergyUnit,
            Double etchScoreDelta,
            String etchScoreUnit
    ) {
    }
}
