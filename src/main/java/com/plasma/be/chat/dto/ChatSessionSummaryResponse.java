package com.plasma.be.chat.dto;

import java.time.LocalDateTime;

public record ChatSessionSummaryResponse(
        String sessionId,
        String title,
        LocalDateTime lastMessageAt,
        int messageCount
) {
}
