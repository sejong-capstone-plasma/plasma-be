package com.plasma.be.predict.controller;

import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.predict.dto.PredictTestRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
public class PredictTestController implements PredictTestApi {

    private final PredictClient predictClient;

    public PredictTestController(PredictClient predictClient) {
        this.predictClient = predictClient;
    }

    @Override
    public ResponseEntity<PredictPipelineResponse> predictRaw(PredictTestRequest request) {
        validate(request);

        PredictPipelineResponse response = predictClient.requestPredictPipeline(
                request.processType(),
                Map.of(
                        "pressure",     request.pressure().value(),
                        "source_power", request.sourcePower().value(),
                        "bias_power",   request.biasPower().value()
                ),
                Map.of(
                        "pressure",     request.pressure().unit(),
                        "source_power", request.sourcePower().unit(),
                        "bias_power",   request.biasPower().unit()
                ),
                request.originalUserInput()
        );
        return ResponseEntity.ok(response);
    }

    private void validate(PredictTestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.originalUserInput() == null || request.originalUserInput().isBlank()) {
            throw new IllegalArgumentException("originalUserInput is required.");
        }
        if (request.processType() == null || request.processType().isBlank()) {
            throw new IllegalArgumentException("processType is required.");
        }
        if (request.pressure() == null || request.sourcePower() == null || request.biasPower() == null) {
            throw new IllegalArgumentException("pressure, sourcePower, biasPower are all required.");
        }
        if (request.pressure().value() == null || request.sourcePower().value() == null || request.biasPower().value() == null) {
            throw new IllegalArgumentException("parameter value is required.");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientException e) {
        return ResponseEntity.internalServerError().body(Map.of("message", "AI server error: " + e.getMessage()));
    }
}
