package com.plasma.be.extract.service;

import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ParametersResponse;
import com.plasma.be.extract.entity.Parameters;
import com.plasma.be.extract.repository.ParametersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ExtractService {

    private final ExtractClient extractClient;
    private final ParametersRepository parametersRepository;

    public ExtractService(ExtractClient extractClient,
                          ParametersRepository parametersRepository) {
        this.extractClient = extractClient;
        this.parametersRepository = parametersRepository;
    }

    // 채팅 메시지에서 파라미터를 추출하고 DB에 저장한 뒤 응답을 반환한다.
    @Transactional
    public ParametersResponse extractAndSave(ChatMessage chatMessage) {
        ExtractedParameterData data = requestToAiServer(chatMessage.getInputText());
        validateExtractedData(data);
        Parameters params = createParameters(chatMessage, data);
        parametersRepository.save(params);
        return toResponse(params);
    }

    ExtractedParameterData requestToAiServer(String message) {
        return extractClient.requestExtraction(message);
    }

    Parameters createParameters(ChatMessage chatMessage, ExtractedParameterData data) {
        Double pressure = extractValue(data.processParams() != null ? data.processParams().pressure() : null);
        Double sourcePower = extractValue(data.processParams() != null ? data.processParams().sourcePower() : null);
        Double biasPower = extractValue(data.processParams() != null ? data.processParams().biasPower() : null);
        Double currentEr = extractCurrentEr(data);

        return Parameters.create(
                data.requestId(),
                chatMessage,
                pressure,
                sourcePower,
                biasPower,
                currentEr
        );
    }

    private Double extractValue(ExtractedParameterData.ValidatedParam param) {
        return (param != null) ? param.value() : null;
    }

    private Double extractCurrentEr(ExtractedParameterData data) {
        if (data.currentOutputs() != null && data.currentOutputs().etchRate() != null) {
            return data.currentOutputs().etchRate().value();
        }
        return null;
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

    private String buildFieldValidationError(ExtractedParameterData data) {
        StringBuilder sb = new StringBuilder("Some parameters could not be extracted properly.");
        if (data.processParams() != null) {
            appendStatus(sb, "pressure", data.processParams().pressure());
            appendStatus(sb, "source_power", data.processParams().sourcePower());
            appendStatus(sb, "bias_power", data.processParams().biasPower());
        }
        return sb.toString();
    }

    private void appendStatus(StringBuilder sb, String name, ExtractedParameterData.ValidatedParam param) {
        if (param != null && !"VALID".equals(param.status())) {
            sb.append(" [").append(name).append(": ").append(param.status()).append("]");
        }
    }

    // requestId로 단건 조회한다.
    @Transactional(readOnly = true)
    public Optional<ParametersResponse> findById(String requestId) {
        return parametersRepository.findById(requestId)
                .map(this::toResponse);
    }

    // ChatMessage ID로 연결된 파라미터 목록을 조회한다.
    @Transactional(readOnly = true)
    public List<ParametersResponse> findByMessageId(Long messageId) {
        return parametersRepository.findByChatMessageMessageId(messageId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Parameters 엔티티를 응답 DTO로 변환한다.
    ParametersResponse toResponse(Parameters params) {
        return new ParametersResponse(
                params.getRequestId(),
                params.getChatMessage().getMessageId(),
                params.getPressureMtorr(),
                params.getSourcePowerW(),
                params.getBiasPowerW(),
                params.getCurrentEr()
        );
    }
}
