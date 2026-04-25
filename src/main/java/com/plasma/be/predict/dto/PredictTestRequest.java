package com.plasma.be.predict.dto;

public record PredictTestRequest(
        String originalUserInput,
        String processType,
        ParamValue pressure,
        ParamValue sourcePower,
        ParamValue biasPower
) {
    public record ParamValue(Double value, String unit) {}
}
