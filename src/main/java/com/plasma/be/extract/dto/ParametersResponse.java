package com.plasma.be.extract.dto;

public record ParametersResponse(
        String requestId,
        Long messageId,
        Double pressureMtorr,
        Double sourcePowerW,
        Double biasPowerW,
        Double currentEr
) {}
