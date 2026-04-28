package com.plasma.be.optimize.controller;

import com.plasma.be.optimize.client.OptimizeClient;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
public class OptimizeController implements OptimizeApi {

    private final OptimizeClient optimizeClient;

    public OptimizeController(OptimizeClient optimizeClient) {
        this.optimizeClient = optimizeClient;
    }

    @Override
    public ResponseEntity<OptimizePipelineResponse> optimizeRaw(OptimizeRequest request) {
        validate(request);
        OptimizePipelineResponse response = optimizeClient.requestOptimizePipeline(request);
        return ResponseEntity.ok(response);
    }

    private void validate(OptimizeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.originalUserInput() == null || request.originalUserInput().isBlank()) {
            throw new IllegalArgumentException("originalUserInput is required.");
        }
        if (request.processType() == null || request.processType().isBlank()) {
            throw new IllegalArgumentException("processType is required.");
        }
        if (request.processParams() == null || request.processParams().isNull() || !request.processParams().isObject()
                || request.processParams().size() == 0) {
            throw new IllegalArgumentException("processParams must be a non-empty object.");
        }
        if (request.currentOutputs() != null && !request.currentOutputs().isNull() && !request.currentOutputs().isObject()) {
            throw new IllegalArgumentException("currentOutputs must be an object.");
        }
        if (request.targetOutputs() != null && !request.targetOutputs().isNull() && !request.targetOutputs().isObject()) {
            throw new IllegalArgumentException("targetOutputs must be an object.");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI server error: " + exception.getMessage()));
    }
}
