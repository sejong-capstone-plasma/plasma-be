package com.plasma.be.plasma.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlasmaExtractRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("user_input") String userInput
) {}
