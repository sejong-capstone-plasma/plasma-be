package com.plasma.be.extract.controller;

import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ExtractTestRequest;
import com.plasma.be.extract.dto.ExtractionResponse;
import com.plasma.be.extract.entity.ProcessParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@RestController
public class ExtractTestController implements ExtractTestApi {

    private final ExtractClient extractClient;

    public ExtractTestController(ExtractClient extractClient) {
        this.extractClient = extractClient;
    }

    @Override
    public ResponseEntity<?> ping() {
        try {
            extractClient.requestExtraction("테스트: 압력 50mTorr, 소스파워 500W, 바이어스파워 50W");
            return ResponseEntity.ok(Map.of("status", "ok", "message", "AI server is reachable"));
        } catch (RestClientException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "AI server error: " + e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<ExtractedParameterData> extractRaw(ExtractTestRequest request) {
        validateInput(request);
        ExtractedParameterData data = extractClient.requestExtraction(request.userInput().trim());
        return ResponseEntity.ok(data);
    }

    @Override
    public ResponseEntity<ExtractionResponse> extract(ExtractTestRequest request) {
        validateInput(request);
        ExtractedParameterData data = extractClient.requestExtraction(request.userInput().trim());
        validateExtractedData(data);
        return ResponseEntity.ok(toResponse(data));
    }

    private void validateInput(ExtractTestRequest request) {
        if (request == null || request.userInput() == null || request.userInput().isBlank()) {
            throw new IllegalArgumentException("userInput is required.");
        }
    }

    private void validateExtractedData(ExtractedParameterData data) {
        if ("UNSUPPORTED".equals(data.validationStatus())) {
            throw new IllegalArgumentException(
                    "Unsupported process or task type: processType=" + data.processType()
                            + ", taskType=" + data.taskType());
        }
        if ("INVALID_FIELD".equals(data.validationStatus())) {
            StringBuilder sb = new StringBuilder("Some parameters could not be extracted properly.");
            if (data.processParams() != null) {
                appendStatus(sb, "pressure", data.processParams().pressure());
                appendStatus(sb, "source_power", data.processParams().sourcePower());
                appendStatus(sb, "bias_power", data.processParams().biasPower());
            }
            throw new IllegalArgumentException(sb.toString());
        }
    }

    private ExtractionResponse toResponse(ExtractedParameterData data) {
        Map<String, ExtractionResponse.ProcessParamResponse> params = new HashMap<>();
        if (data.processParams() != null) {
            addParam(params, "pressure", data.processParams().pressure());
            addParam(params, "source_power", data.processParams().sourcePower());
            addParam(params, "bias_power", data.processParams().biasPower());
        }

        ExtractionResponse.ProcessParamResponse currentEr = null;
        if (data.currentOutputs() != null && data.currentOutputs().etchRate() != null) {
            var er = data.currentOutputs().etchRate();
            currentEr = new ExtractionResponse.ProcessParamResponse(er.value(), er.unit(), er.status());
        }

        return new ExtractionResponse(
                data.requestId(),
                data.validationStatus(),
                data.processType(),
                data.taskType(),
                params,
                currentEr
        );
    }

    private void addParam(Map<String, ExtractionResponse.ProcessParamResponse> params,
                          String key, ExtractedParameterData.ValidatedParam param) {
        if (param != null) {
            params.put(key, new ExtractionResponse.ProcessParamResponse(param.value(), param.unit(), param.status()));
        }
    }

    private void appendStatus(StringBuilder sb, String name, ExtractedParameterData.ValidatedParam param) {
        if (param != null && !"VALID".equals(param.status())) {
            sb.append(" [").append(name).append(": ").append(param.status()).append("]");
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
