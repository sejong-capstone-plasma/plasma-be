package com.plasma.be.optimize.client;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class OptimizeClient {

    private static final String OPTIMIZE_PIPELINE_ENDPOINT = "/ai/pipelines/optimize";

    private final RestClient httpClient;

    public OptimizeClient(@Qualifier("extractRestClient") RestClient restClient) {
        this.httpClient = restClient;
    }

    public OptimizePipelineResponse requestOptimizePipeline(OptimizeRequest request) {
        return httpClient.post()
                .uri(OPTIMIZE_PIPELINE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildBody(request))
                .retrieve()
                .body(OptimizePipelineResponse.class);
    }

    ObjectNode buildBody(OptimizeRequest request) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("original_user_input", request.originalUserInput());
        body.put("process_type", request.processType());
        body.set("process_params", copyNodeOrEmptyObject(request.processParams()));

        if (hasNode(request.currentOutputs())) {
            body.set("current_outputs", request.currentOutputs().deepCopy());
        }
        if (hasNode(request.targetOutputs())) {
            body.set("target_outputs", request.targetOutputs().deepCopy());
        }
        return body;
    }

    private com.fasterxml.jackson.databind.JsonNode copyNodeOrEmptyObject(com.fasterxml.jackson.databind.JsonNode node) {
        if (hasNode(node)) {
            return node.deepCopy();
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private boolean hasNode(com.fasterxml.jackson.databind.JsonNode node) {
        return node != null && !node.isNull();
    }
}
