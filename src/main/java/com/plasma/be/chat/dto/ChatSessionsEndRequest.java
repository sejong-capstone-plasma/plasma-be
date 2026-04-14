package com.plasma.be.chat.dto;

import java.util.List;

public record ChatSessionsEndRequest(
        List<String> sessionIds
) {
}
