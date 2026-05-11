package com.plasma.be.chat.dto;

import com.plasma.be.compare.dto.ComparisonResponse;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.question.client.dto.QuestionAnswerResponse;

public record ConfirmResponse(
        ParameterValidationResponse validation,
        PredictPipelineResponse prediction,
        PlasmaDistributionResponse plasmaDistribution,
        ConfirmOptimizationResponse optimization,
        ComparisonResponse comparison,
        QuestionAnswerResponse question,
        String predictionError,
        String executionError
) {
}
