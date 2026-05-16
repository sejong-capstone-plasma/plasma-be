package com.plasma.be.question.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionClientTest {

    private final QuestionClient questionClient = new QuestionClient(
            RestClient.builder().baseUrl("http://localhost:8000").build()
    );

    @Test
    void buildBody_질문과_history를_포함한다() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "압력 50mTorr 예측해줘"),
                Map.of("role", "assistant", "content", "etch score 75.0 point 수준입니다.")
        );

        Map<String, Object> body = questionClient.buildBody("ion flux가 뭐야?", history);

        assertThat(body.get("request_id")).isNotNull();
        assertThat(body.get("original_user_input")).isEqualTo("ion flux가 뭐야?");
        assertThat(body.get("question")).isEqualTo("ion flux가 뭐야?");
        assertThat(body.get("history")).isEqualTo(history);
    }
}
