package com.plasma.be.extract.service;

import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ExtractionResponse;
import com.plasma.be.extract.entity.ExtractionResult;
import com.plasma.be.extract.entity.ProcessParameter;
import com.plasma.be.extract.repository.ExtractionResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class ExtractService {

    private final ExtractClient extractClient;
    private final ExtractionResultRepository extractionResultRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ExtractService(ExtractClient extractClient,
                          ExtractionResultRepository extractionResultRepository,
                          ChatMessageRepository chatMessageRepository) {
        this.extractClient = extractClient;
        this.extractionResultRepository = extractionResultRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public ExtractionResponse extractFromMessage(ChatMessage chatMessage) {
        ExtractedParameterData data = requestToAiServer(chatMessage.getInputText());
        validateExtractedData(data);
        ExtractionResult result = createExtractionResult(chatMessage, data);
        saveExtractionResult(result);
        return buildExtractionResponse(result);
    }

    ExtractedParameterData requestToAiServer(String message) {
        return extractClient.requestExtraction(message);
    }

    ExtractionResult createExtractionResult(ChatMessage chatMessage, ExtractedParameterData data) {
        ExtractionResult result = ExtractionResult.createExtractionResult(
                chatMessage,
                data.requestId(),
                data.taskType(),
                data.processType()
        );

        if (data.processParams() != null) {
            addParamIfPresent(result, "pressure",    data.processParams().pressure());
            addParamIfPresent(result, "source_power", data.processParams().sourcePower());
            addParamIfPresent(result, "bias_power",   data.processParams().biasPower());
        }

        if (data.currentOutputs() != null && data.currentOutputs().etchRate() != null) {
            ExtractedParameterData.ValidatedParam er = data.currentOutputs().etchRate();
            result.setCurrentEr(ProcessParameter.createParameter(er.value(), er.unit(), er.status()));
        }

        result.updateStatus(data.validationStatus());
        return result;
    }

    boolean saveExtractionResult(ExtractionResult extractionResult) {
        extractionResultRepository.save(extractionResult);
        return true;
    }

    ExtractionResponse buildExtractionResponse(ExtractionResult result) {
        Map<String, ExtractionResponse.ProcessParamResponse> params = new HashMap<>();
        result.getProcessParams().forEach((key, pp) ->
                params.put(key, new ExtractionResponse.ProcessParamResponse(pp.getValue(), pp.getUnit(), pp.getStatus()))
        );

        ExtractionResponse.ProcessParamResponse currentEr = null;
        if (result.getCurrentEr() != null) {
            ProcessParameter er = result.getCurrentEr();
            currentEr = new ExtractionResponse.ProcessParamResponse(er.getValue(), er.getUnit(), er.getStatus());
        }

        return new ExtractionResponse(
                result.getRequestId(),
                result.getExtractionStatus(),
                result.getProcessType(),
                result.getTaskType(),
                params,
                currentEr
        );
    }

    boolean validateExtractedData(ExtractedParameterData data) {
        if (data == null) {
            throw new IllegalStateException("No response received from AI server.");
        }
        if ("UNSUPPORTED".equals(data.validationStatus())) {
            throw new IllegalArgumentException(
                    "Unsupported process or task type: processType=" + data.processType()
                            + ", taskType=" + data.taskType());
        }
        if ("INVALID_FIELD".equals(data.validationStatus())) {
            throw new IllegalArgumentException(buildFieldValidationError(data));
        }
        return true;
    }

    private void addParamIfPresent(ExtractionResult result, String key,
                                   ExtractedParameterData.ValidatedParam param) {
        if (param != null) {
            result.addProcessParam(key, ProcessParameter.createParameter(
                    param.value(), param.unit(), param.status()));
        }
    }

    private String buildFieldValidationError(ExtractedParameterData data) {
        StringBuilder sb = new StringBuilder("Some parameters could not be extracted properly.");
        if (data.processParams() != null) {
            appendStatus(sb, "pressure",    data.processParams().pressure());
            appendStatus(sb, "source_power", data.processParams().sourcePower());
            appendStatus(sb, "bias_power",   data.processParams().biasPower());
        }
        return sb.toString();
    }

    private void appendStatus(StringBuilder sb, String name, ExtractedParameterData.ValidatedParam param) {
        if (param != null && !"VALID".equals(param.status())) {
            sb.append(" [").append(name).append(": ").append(param.status()).append("]");
        }
    }
}
