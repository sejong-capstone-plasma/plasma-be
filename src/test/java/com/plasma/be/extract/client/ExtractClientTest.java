package com.plasma.be.extract.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

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
    @SuppressWarnings("unchecked")
    void buildValidateBody_올바른_중첩_구조를_생성한다() {
        Map<String, Double> values = Map.of("pressure", 30.0, "source_power", 500.0, "bias_power", 100.0);
        Map<String, String> units  = Map.of("pressure", "mTorr", "source_power", "W", "bias_power", "W");

        Map<String, Object> body = extractClient.buildValidateBody("ETCH", "PREDICTION", values, units);

        assertThat(body).containsEntry("process_type", "ETCH");
        assertThat(body).containsEntry("task_type", "PREDICTION");
        assertThat(body.get("request_id")).isNotNull();
        assertThat(body.get("current_outputs")).isNull();

        Map<String, Object> processParams = (Map<String, Object>) body.get("process_params");
        assertThat(processParams).containsKeys("pressure", "source_power", "bias_power");

        Map<String, Object> pressure = (Map<String, Object>) processParams.get("pressure");
        assertThat(pressure).contains(entry("value", 30.0), entry("unit", "mTorr"));
    }

    @Test
    void handleClientError_상태코드를_503으로_기록한다() {
        boolean handled = extractClient.handleClientError("connection refused");

        assertThat(handled).isFalse();
        assertThat(extractClient.getLastResponseStatus()).isEqualTo(503);
    }
}
