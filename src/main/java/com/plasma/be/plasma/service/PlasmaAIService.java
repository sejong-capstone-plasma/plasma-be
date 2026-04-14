package com.plasma.be.plasma.service;

import com.plasma.be.plasma.client.PlasmaAIClient;
import com.plasma.be.plasma.client.dto.PlasmaExtractResponse;
import com.plasma.be.plasma.dto.ExtractParametersRequest;
import com.plasma.be.plasma.dto.ExtractParametersResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class PlasmaAIService {

    private final PlasmaAIClient plasmaAIClient;

    public PlasmaAIService(PlasmaAIClient plasmaAIClient) {
        this.plasmaAIClient = plasmaAIClient;
    }

    public ExtractParametersResponse extractParameters(ExtractParametersRequest request) {
        validate(request);

        String requestId = UUID.randomUUID().toString();
        PlasmaExtractResponse aiResponse = plasmaAIClient.extractParameters(requestId, request.userInput().trim());

        if (!"VALID".equals(aiResponse.validationStatus())) {
            throw new IllegalArgumentException(buildValidationErrorMessage(aiResponse));
        }

        return toResponse(aiResponse);
    }

    private void validate(ExtractParametersRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.sessionId())) {
            throw new IllegalArgumentException("sessionId is required.");
        }
        if (!StringUtils.hasText(request.userInput())) {
            throw new IllegalArgumentException("userInput is required.");
        }
    }

    private String buildValidationErrorMessage(PlasmaExtractResponse response) {
        if ("UNSUPPORTED".equals(response.validationStatus())) {
            return "Unsupported process or task type: processType=" + response.processType()
                    + ", taskType=" + response.taskType();
        }

        if (response.processParams() == null) {
            return "Parameter extraction failed: " + response.validationStatus();
        }

        StringBuilder sb = new StringBuilder("Some parameters could not be extracted properly.");
        appendParamStatus(sb, "pressure", response.processParams().pressure());
        appendParamStatus(sb, "sourcePower", response.processParams().sourcePower());
        appendParamStatus(sb, "biasPower", response.processParams().biasPower());
        return sb.toString();
    }

    private void appendParamStatus(StringBuilder sb, String name, PlasmaExtractResponse.ValidatedValueWithUnit param) {
        if (param != null && !"VALID".equals(param.status())) {
            sb.append(" [").append(name).append(": ").append(param.status()).append("]");
        }
    }

    private ExtractParametersResponse toResponse(PlasmaExtractResponse ai) {
        ExtractParametersResponse.ProcessParams processParams = null;
        if (ai.processParams() != null) {
            PlasmaExtractResponse.ProcessParams src = ai.processParams();
            processParams = new ExtractParametersResponse.ProcessParams(
                    toValidated(src.pressure()),
                    toValidated(src.sourcePower()),
                    toValidated(src.biasPower())
            );
        }

        ExtractParametersResponse.CurrentOutputs currentOutputs = null;
        if (ai.currentOutputs() != null && ai.currentOutputs().etchRate() != null) {
            PlasmaExtractResponse.ValueWithUnit etchRate = ai.currentOutputs().etchRate();
            currentOutputs = new ExtractParametersResponse.CurrentOutputs(
                    new ExtractParametersResponse.ValueWithUnit(etchRate.value(), etchRate.unit())
            );
        }

        return new ExtractParametersResponse(
                ai.requestId(),
                ai.validationStatus(),
                ai.processType(),
                ai.taskType(),
                processParams,
                currentOutputs
        );
    }

    private ExtractParametersResponse.ValidatedValueWithUnit toValidated(PlasmaExtractResponse.ValidatedValueWithUnit src) {
        if (src == null) return null;
        return new ExtractParametersResponse.ValidatedValueWithUnit(src.value(), src.unit(), src.status());
    }
}
