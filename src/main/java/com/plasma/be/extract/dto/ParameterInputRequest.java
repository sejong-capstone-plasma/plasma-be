package com.plasma.be.extract.dto;

public record ParameterInputRequest(
        String key,
        Double value,
        String unit
) {
}
