package com.plasma.be.predict.dto;

import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;

public record ConfirmResponse(
        ParameterValidationResponse validation,
        PredictPipelineResponse prediction
) {
}
