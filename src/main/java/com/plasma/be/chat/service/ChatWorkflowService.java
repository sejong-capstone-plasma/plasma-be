package com.plasma.be.chat.service;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.service.ExtractService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatWorkflowService {

    private final ChatMessageService chatMessageService;
    private final ExtractService extractService;

    public ChatWorkflowService(ChatMessageService chatMessageService, ExtractService extractService) {
        this.chatMessageService = chatMessageService;
        this.extractService = extractService;
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

    // 최종 검증 결과를 확정 상태로 전환한다.
    public ParameterValidationResponse confirmValidation(Long messageId,
                                                         Long validationId,
                                                         String ownerSessionKey) {
        chatMessageService.findOwnedMessage(messageId, ownerSessionKey);
        return extractService.confirmValidation(messageId, validationId)
                .orElseThrow(() -> new NoSuchElementException("validationId is not associated with the message."));
    }
}
