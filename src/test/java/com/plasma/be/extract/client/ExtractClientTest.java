package com.plasma.be.extract.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractClientTest {

    private final ExtractClient extractClient = new ExtractClient(
            RestClient.builder().baseUrl("http://localhost:8000").build(),
            "http://localhost:8000",
            130
    );

    @Test
    void buildRequestBody_스네이크케이스_필드를_생성한다() {
        Map<String, String> requestBody = extractClient.buildRequestBody("압력 50mTorr 식각률 예측");

        assertThat(requestBody).containsEntry("user_input", "압력 50mTorr 식각률 예측");
        assertThat(requestBody.get("request_id")).isNotBlank();
    }

    @Test
    void handleClientError_상태코드를_503으로_기록한다() {
        boolean handled = extractClient.handleClientError("connection refused");

        assertThat(handled).isFalse();
        assertThat(extractClient.getLastResponseStatus()).isEqualTo(503);
    }
}
