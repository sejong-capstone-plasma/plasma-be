package com.plasma.be.compare.client;

import com.plasma.be.compare.dto.ComparisonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CompareClient {

    private static final String COMPARE_PIPELINE_ENDPOINT = "/ai/pipelines/compare";
    private static final Logger log = LoggerFactory.getLogger(CompareClient.class);

    private final RestClient httpClient;

    public CompareClient(@Qualifier("extractRestClient") RestClient restClient) {
        this.httpClient = restClient;
    }

    public ComparisonResponse requestComparePipeline(String processType,
                                                     Map<String, Double> leftParamValues,
                                                     Map<String, String> leftParamUnits,
                                                     Map<String, Double> rightParamValues,
                                                     Map<String, String> rightParamUnits,
                                                     String originalUserInput) {
        Map<String, Object> body = buildBody(
                processType,
                leftParamValues,
                leftParamUnits,
                rightParamValues,
                rightParamUnits,
                originalUserInput
        );
        log.info("Compare upstream request body: {}", body);

        try {
            return httpClient.post()
                    .uri(COMPARE_PIPELINE_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ComparisonResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Compare upstream error status={} body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString());
            throw exception;
        }
    }

    Map<String, Object> buildBody(String processType,
                                  Map<String, Double> leftParamValues,
                                  Map<String, String> leftParamUnits,
                                  Map<String, Double> rightParamValues,
                                  Map<String, String> rightParamUnits,
                                  String originalUserInput) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("original_user_input", originalUserInput);
        body.put("process_type", processType);
        body.put("condition_a", buildCondition(leftParamValues, leftParamUnits));
        body.put("condition_b", buildCondition(rightParamValues, rightParamUnits));
        return body;
    }

    private Map<String, Object> buildCondition(Map<String, Double> paramValues,
                                               Map<String, String> paramUnits) {
        Map<String, Object> condition = new LinkedHashMap<>();
        for (String key : List.of("pressure", "source_power", "bias_power")) {
            condition.put(key, Map.of(
                    "value", paramValues.getOrDefault(key, 0.0),
                    "unit", paramUnits.getOrDefault(key, "")
            ));
        }
        return condition;
    }
}
