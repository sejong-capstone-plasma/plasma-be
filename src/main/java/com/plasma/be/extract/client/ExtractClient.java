package com.plasma.be.extract.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class ExtractClient {

    private final String baseUrl;
    private final String extractEndpoint;
    private final int timeout;
    private final RestClient httpClient;
    private final ObjectMapper objectMapper;
    private int lastResponseStatus;

    public ExtractClient(
            RestClient extractRestClient,
            ObjectMapper objectMapper,
            @Value("${plasma.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${plasma.ai.timeout-seconds:130}") int timeout) {
        this.httpClient = extractRestClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.extractEndpoint = "/ai/services/extract-parameters";
        this.timeout = timeout;
    }

    public ExtractedParameterData requestExtraction(String message) {
        String body = buildRequestBody(message);
        String response = sendRequest(baseUrl + extractEndpoint, body);
        return parseResponse(response);
    }

    String buildRequestBody(String message) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("request_id", UUID.randomUUID().toString(), "user_input", message)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build request body", e);
        }
    }

    String sendRequest(String url, String body) {
        try {
            String response = httpClient.post()
                    .uri(extractEndpoint)
                    .body(body)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .body(String.class);
            this.lastResponseStatus = 200;
            return response;
        } catch (RestClientException e) {
            handleClientError(e.getMessage());
            throw e;
        }
    }

    ExtractedParameterData parseResponse(String response) {
        try {
            return objectMapper.readValue(response, ExtractedParameterData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse AI server response", e);
        }
    }

    boolean handleClientError(String errorMessage) {
        this.lastResponseStatus = 503;
        return false;
    }

    public int getLastResponseStatus() {
        return lastResponseStatus;
    }
}
