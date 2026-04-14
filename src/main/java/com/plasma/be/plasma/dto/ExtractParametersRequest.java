package com.plasma.be.plasma.dto;

public record ExtractParametersRequest(
        String sessionId,
        String userInput
) {}
