package com.plasma.be.extract.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ExtractClient {

    private final String baseUrl;
    private final String extractEndpoint;
    private final String validateEndpoint;
    private final int timeout;
    private final RestClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private int lastResponseStatus;

    public ExtractClient(
            @Qualifier("extractRestClient") RestClient restClient,
            @Value("${plasma.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${plasma.ai.timeout-seconds:130}") int timeout) {
        this.httpClient = restClient;
        this.baseUrl = baseUrl;
        this.extractEndpoint = "/ai/services/extract-parameters";
        this.validateEndpoint = "/ai/services/extract-validate";
        this.timeout = timeout;
    }

    public ExtractedParameterData requestExtraction(String message, List<Map<String, String>> history) {
        Map<String, Object> requestBody = buildRequestBody(message, history);
        return sendRequest(requestBody);
    }

    public ExtractedParameterData requestValidation(String processType,
                                                    String taskType,
                                                    Map<String, Double> paramValues,
                                                    Map<String, String> paramUnits) {
        Map<String, Object> body = buildValidateBody(processType, taskType, paramValues, paramUnits);
        return sendValidateRequest(body);
    }

    Map<String, Object> buildRequestBody(String message, List<Map<String, String>> history) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("user_input", message);
        body.put("history", history != null ? history : List.of());
        return body;
    }

    Map<String, Object> buildValidateBody(String processType,
                                          String taskType,
                                          Map<String, Double> paramValues,
                                          Map<String, String> paramUnits) {
        Map<String, Object> processParamsMap = new LinkedHashMap<>();
        for (String key : List.of("pressure", "source_power", "bias_power")) {
            processParamsMap.put(key, Map.of(
                    "value", paramValues.getOrDefault(key, 0.0),
                    "unit",  paramUnits.getOrDefault(key, "")));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("process_type", processType);
        if (taskType != null) {
            body.put("task_type", taskType);
        }
        body.put("process_params", processParamsMap);
        body.put("current_outputs", null);
        return body;
    }

    ExtractedParameterData sendRequest(Map<String, Object> body) {
        try {
            String responseBody = httpClient.post()
                    .uri(extractEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            ExtractedParameterData response = parseResponse(responseBody);
            this.lastResponseStatus = 200;
            return response;
        } catch (RestClientException e) {
            handleClientError(e.getMessage());
            throw e;
        }
    }

    private ExtractedParameterData sendValidateRequest(Map<String, Object> body) {
        try {
            String responseBody = httpClient.post()
                    .uri(validateEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            ExtractedParameterData response = parseResponse(responseBody);
            this.lastResponseStatus = 200;
            return response;
        } catch (RestClientException e) {
            handleClientError(e.getMessage());
            throw e;
        }
    }

    ExtractedParameterData parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode payload = root.has("extract") ? root.get("extract") : root;
            if (payload == null || payload.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(payload, ExtractedParameterData.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse extract response.", exception);
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
