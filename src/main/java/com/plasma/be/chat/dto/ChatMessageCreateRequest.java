package com.plasma.be.chat.dto;

public record ChatMessageCreateRequest(
        String sessionId,
        String role,
        String content,
        String inputText
) {
}
