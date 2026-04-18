package com.plasma.be.extract.dto;

import java.util.List;

public record ParameterValidationRequest(
        List<ParameterInputRequest> parameters
) {
}
