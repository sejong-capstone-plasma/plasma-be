package com.plasma.be.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageSummaryResponse(
        Long messageId,
        String sessionId,
        String inputText,
        LocalDateTime createdAt
) {
}
