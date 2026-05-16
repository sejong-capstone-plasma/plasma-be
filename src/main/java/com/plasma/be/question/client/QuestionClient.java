package com.plasma.be.question.client;

import com.plasma.be.question.client.dto.QuestionAnswerResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class QuestionClient {

    private static final String QUESTION_PIPELINE_ENDPOINT = "/ai/pipelines/question";

    private final RestClient httpClient;

    public QuestionClient(@Qualifier("extractRestClient") RestClient restClient) {
        this.httpClient = restClient;
    }

    public QuestionAnswerResponse requestAnswer(String question, List<Map<String, String>> history) {
        Map<String, Object> body = buildBody(question, history);
        return httpClient.post()
                .uri(QUESTION_PIPELINE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(QuestionAnswerResponse.class);
    }

    Map<String, Object> buildBody(String question, List<Map<String, String>> history) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("original_user_input", question);
        body.put("question", question);
        body.put("history", history == null ? List.of() : history);
        return body;
    }
}
