package com.plasma.be.extract.dto;

public record ParameterFieldResponse(
        String key,
        String label,
        Double value,
        String unit,
        String status
) {
}
