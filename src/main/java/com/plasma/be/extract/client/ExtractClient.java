package com.plasma.be.extract.client;

import com.plasma.be.extract.client.dto.ExtractedParameterData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    private int lastResponseStatus;

    public ExtractClient(
            RestClient extractRestClient,
            @Value("${plasma.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${plasma.ai.timeout-seconds:130}") int timeout) {
        this.httpClient = extractRestClient;
        this.baseUrl = baseUrl;
        this.extractEndpoint = "/ai/services/extract-parameters";
        this.timeout = timeout;
    }

    public ExtractedParameterData requestExtraction(String message) {
        Map<String, String> requestBody = buildRequestBody(message);
        return sendRequest(requestBody);
    }

    Map<String, String> buildRequestBody(String message) {
        return Map.of(
                "request_id", UUID.randomUUID().toString(),
                "user_input", message
        );
    }

    ExtractedParameterData sendRequest(Map<String, String> body) {
        try {
            ExtractedParameterData response = httpClient.post()
                    .uri(extractEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ExtractedParameterData.class);
            this.lastResponseStatus = 200;
            return response;
        } catch (RestClientException e) {
            handleClientError(e.getMessage());
            throw e;
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
