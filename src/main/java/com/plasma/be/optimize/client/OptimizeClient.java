package com.plasma.be.optimize.client;

import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OptimizeClient {

    private static final String OPTIMIZE_PIPELINE_ENDPOINT = "/ai/pipelines/optimize";
    private static final Logger log = LoggerFactory.getLogger(OptimizeClient.class);

    private final RestClient httpClient;

    public OptimizeClient(@Qualifier("extractRestClient") RestClient restClient) {
        this.httpClient = restClient;
    }

    public OptimizePipelineResponse requestOptimizePipeline(OptimizeRequest request) {
        Map<String, Object> body = buildBody(request);
        log.info("Optimize upstream request body: {}", body);

        try {
            return httpClient.post()
                    .uri(OPTIMIZE_PIPELINE_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OptimizePipelineResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Optimize upstream error status={} body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString());
            throw exception;
        }
    }

    Map<String, Object> buildBody(OptimizeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("original_user_input", request.originalUserInput());
        body.put("process_type", request.processType());
        body.put("process_params", copyMapOrEmpty(request.processParams()));

        if (hasMap(request.currentOutputs())) {
            body.put("current_outputs", new LinkedHashMap<>(request.currentOutputs()));
        }
        return body;
    }

    private Map<String, Object> copyMapOrEmpty(Map<String, Object> source) {
        if (hasMap(source)) {
            return new LinkedHashMap<>(source);
        }
        return new LinkedHashMap<>();
    }

    private boolean hasMap(Map<String, Object> source) {
        return source != null && !source.isEmpty();
    }
}
