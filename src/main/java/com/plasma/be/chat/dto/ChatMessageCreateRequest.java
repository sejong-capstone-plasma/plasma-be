package com.plasma.be.chat.dto;

public record ChatMessageCreateRequest(
        String sessionId,
        String inputText
) {
}
