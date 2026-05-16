package com.plasma.be.chat.dto;

import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.question.client.dto.QuestionAnswerResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageSummaryResponse(
        Long messageId,
        String sessionId,
        String role,
        String inputText,
        LocalDateTime createdAt,
        List<ParameterValidationResponse> validations,
        QuestionAnswerResponse question
) {
}
