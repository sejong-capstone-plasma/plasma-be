package com.plasma.be.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageCreateResponse(
        Long messageId,
        String sessionId,
        String inputText,
        LocalDateTime savedAt
) {
}
