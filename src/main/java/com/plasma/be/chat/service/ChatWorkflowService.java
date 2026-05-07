package com.plasma.be.chat.service;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.compare.dto.ComparisonResponse;
import com.plasma.be.compare.service.ComparisonService;
import com.plasma.be.extract.dto.ParameterFieldResponse;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.service.ExtractService;
import com.plasma.be.optimize.client.OptimizeClient;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.chat.dto.ConfirmResponse;
import com.plasma.be.question.client.dto.QuestionAnswerResponse;
import com.plasma.be.question.service.QuestionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
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
    private final OptimizeClient optimizeClient;
    private final ComparisonService comparisonService;
    private final QuestionService questionService;

    public ChatWorkflowService(ChatMessageService chatMessageService,
                               ExtractService extractService,
                               PredictClient predictClient,
                               OptimizeClient optimizeClient,
                               ComparisonService comparisonService,
                               QuestionService questionService) {
        this.chatMessageService = chatMessageService;
        this.extractService = extractService;
        this.predictClient = predictClient;
        this.optimizeClient = optimizeClient;
        this.comparisonService = comparisonService;
        this.questionService = questionService;
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
                                             String ownerSessionKey,
                                             String requestedTaskType) {
        ChatMessage message = chatMessageService.findOwnedMessage(messageId, ownerSessionKey);
        ParameterValidationResponse validation = extractService.confirmValidation(messageId, validationId)
                .orElseThrow(() -> new NoSuchElementException("validationId is not associated with the message."));

        String taskType = resolveTaskType(validation.taskType(), requestedTaskType);
        if ("COMPARISON".equals(taskType)) {
            try {
                ComparisonResponse comparison = comparisonService.compare(message, validation);
                return new ConfirmResponse(validation, null, null, comparison, null, null);
            } catch (RestClientException e) {
                return new ConfirmResponse(validation, null, null, null, null, e.getMessage());
            }
        }
        if ("OPTIMIZATION".equals(taskType)) {
            try {
                OptimizePipelineResponse optimization = runOptimizePipeline(message, validation);
                return new ConfirmResponse(validation, null, optimization, null, null, null);
            } catch (RestClientException e) {
                return new ConfirmResponse(validation, null, null, null, null, e.getMessage());
            }
        }
        if ("QUESTION".equals(taskType)) {
            try {
                QuestionAnswerResponse question = questionService.answer(message);
                return new ConfirmResponse(validation, null, null, null, question, null);
            } catch (RestClientException e) {
                return new ConfirmResponse(validation, null, null, null, null, e.getMessage());
            }
        }
        if ("UNSUPPORTED".equals(taskType)) {
            return new ConfirmResponse(validation, null, null, null, null, null);
        }
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalArgumentException("requestedTaskType is required when taskType is not inferred.");
        }
        if (!"PREDICTION".equals(taskType)) {
            return new ConfirmResponse(validation, null, null, null, null, null);
        }

        try {
            PredictPipelineResponse prediction = runPredictPipeline(message, validation);
            ParameterValidationResponse updatedValidation =
                    extractService.storePredictionOutcome(messageId, validationId, prediction, null);
            return new ConfirmResponse(updatedValidation, prediction, null, null, null, null);
        } catch (RestClientException e) {
            ParameterValidationResponse updatedValidation =
                    extractService.storePredictionOutcome(messageId, validationId, null, e.getMessage());
            return new ConfirmResponse(updatedValidation, null, null, null, null, e.getMessage());
        }
    }

    private String resolveTaskType(String inferredTaskType, String requestedTaskType) {
        if (StringUtils.hasText(inferredTaskType)) {
            return inferredTaskType;
        }
        if (!StringUtils.hasText(requestedTaskType)) {
            return null;
        }
        String normalized = requestedTaskType.trim().toUpperCase();
        if (!List.of("PREDICTION", "OPTIMIZATION").contains(normalized)) {
            throw new IllegalArgumentException("requestedTaskType must be PREDICTION or OPTIMIZATION.");
        }
        return normalized;
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

    private OptimizePipelineResponse runOptimizePipeline(ChatMessage message,
                                                         ParameterValidationResponse validation) {
        Map<String, Object> processParams = new LinkedHashMap<>();
        for (ParameterFieldResponse parameter : validation.parameters()) {
            processParams.put(parameter.key(), Map.of(
                    "value", parameter.value(),
                    "unit", parameter.unit() == null ? "" : parameter.unit()
            ));
        }

        Map<String, Object> currentOutputs = null;
        if (validation.currentEr() != null && validation.currentEr().value() != null) {
            currentOutputs = Map.of(
                    "etch_rate", Map.of(
                            "value", validation.currentEr().value(),
                            "unit", validation.currentEr().unit() == null ? "" : validation.currentEr().unit()
                    )
            );
        }

        return optimizeClient.requestOptimizePipeline(new OptimizeRequest(
                message.getInputText(),
                validation.processType() != null ? validation.processType() : "ETCH",
                processParams,
                currentOutputs
        ));
    }
}
