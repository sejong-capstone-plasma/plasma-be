package com.plasma.be.question.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record QuestionAnswerResponse(
        @JsonAlias("request_id") String requestId,
        @JsonAlias({"answer_text", "answer"}) String answerText,
        @JsonAlias("answer_source") String answerSource,
        @JsonAlias("sources") List<Object> references
) {
}
