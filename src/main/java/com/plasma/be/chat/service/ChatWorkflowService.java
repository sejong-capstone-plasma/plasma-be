package com.plasma.be.chat.service;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.service.ExtractService;
import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.predict.dto.ConfirmResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatWorkflowService {

    private final ChatMessageService chatMessageService;
    private final ExtractService extractService;
    private final PredictClient predictClient;

    public ChatWorkflowService(ChatMessageService chatMessageService,
                               ExtractService extractService,
                               PredictClient predictClient) {
        this.chatMessageService = chatMessageService;
        this.extractService = extractService;
        this.predictClient = predictClient;
    }

    // 사용자 메시지를 저장하고 첫 번째 AI 추출 결과를 응답으로 반환한다.
    public ChatMessageSummaryResponse createMessageAndExtract(ChatMessageCreateRequest request, String ownerSessionKey) {
        ChatMessage message = chatMessageService.saveMessage(request, ownerSessionKey);
        ParameterValidationResponse validation = extractService.extractFromMessage(message.getMessageId());
        return chatMessageService.toResponse(message, List.of(validation));
    }

    // 메시지별 검증 이력을 포함한 채팅 목록을 조회한다.
    public List<ChatMessageSummaryResponse> findMessagesWithValidations(String sessionId, String ownerSessionKey) {
        List<ChatMessage> messages = chatMessageService.findMessageEntitiesBySessionId(sessionId, ownerSessionKey);
        Map<Long, List<ParameterValidationResponse>> validationsByMessageId = extractService.findByMessageIds(
                messages.stream().map(ChatMessage::getMessageId).toList()
        ).stream().collect(Collectors.groupingBy(
                ParameterValidationResponse::messageId,
                Collectors.mapping(Function.identity(), Collectors.toList())
        ));

        return messages.stream()
                .map(message -> chatMessageService.toResponse(
                        message,
                        validationsByMessageId.getOrDefault(message.getMessageId(), List.of())
                ))
                .toList();
    }

    // 사용자가 수정한 파라미터를 기존 메시지에 연결된 새 검증 시도로 저장한다.
    public ParameterValidationResponse validateCorrection(Long messageId,
                                                          ParameterValidationRequest request,
                                                          String ownerSessionKey) {
        ChatMessage message = chatMessageService.findOwnedMessage(messageId, ownerSessionKey);
        return extractService.validateCorrection(message.getMessageId(), request);
    }

    // 최종 검증 결과를 확정하고, task_type이 PREDICTION이면 예측 파이프라인을 실행한다.
    public ConfirmResponse confirmValidation(Long messageId,
                                             Long validationId,
                                             String ownerSessionKey) {
        ChatMessage message = chatMessageService.findOwnedMessage(messageId, ownerSessionKey);
        ParameterValidationResponse validation = extractService.confirmValidation(messageId, validationId)
                .orElseThrow(() -> new NoSuchElementException("validationId is not associated with the message."));

        if (!"PREDICTION".equals(validation.taskType())) {
            return new ConfirmResponse(validation, null, null);
        }

        try {
            PredictPipelineResponse prediction = runPredictPipeline(message, validation);
            return new ConfirmResponse(validation, prediction, null);
        } catch (RestClientException e) {
            return new ConfirmResponse(validation, null, e.getMessage());
        }
    }

    private PredictPipelineResponse runPredictPipeline(ChatMessage message,
                                                        ParameterValidationResponse validation) {
        Map<String, Double> paramValues = validation.parameters().stream()
                .filter(p -> p.value() != null)
                .collect(Collectors.toMap(ParameterFieldResponse::key, ParameterFieldResponse::value));
        Map<String, String> paramUnits = validation.parameters().stream()
                .filter(p -> p.unit() != null)
                .collect(Collectors.toMap(ParameterFieldResponse::key, ParameterFieldResponse::unit));

        return predictClient.requestPredictPipeline(
                validation.processType() != null ? validation.processType() : "ETCH",
                paramValues,
                paramUnits,
                message.getInputText()
        );
    }
}
