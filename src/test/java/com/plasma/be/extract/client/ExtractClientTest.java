package com.plasma.be.extract.client;

import com.plasma.be.extract.client.dto.ExtractedParameterData;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
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
        Map<String, Object> requestBody = extractClient.buildRequestBody("압력 50mTorr 식각률 예측", List.of());

        assertThat(requestBody).containsEntry("user_input", "압력 50mTorr 식각률 예측");
        assertThat(requestBody).containsEntry("history", List.of());
        assertThat(requestBody.get("request_id")).isNotNull();
    }

    @Test
    void buildRequestBody_히스토리_항목이_포함된다() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "압력 50mTorr 예측해줘"),
                Map.of("role", "assistant", "content", "etch score 75.0 point 수준입니다.")
        );

        Map<String, Object> requestBody = extractClient.buildRequestBody("source power 100W 올려서 다시 예측해줘", history);

        assertThat(requestBody).containsEntry("history", history);
        assertThat(requestBody.get("request_id")).isNotNull();
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

    @Test
    void parseResponse_extract래퍼_응답을_해석한다() {
        ExtractedParameterData response = extractClient.parseResponse("""
                {
                  "extract": {
                    "request_id": "req-001",
                    "validation_status": "VALID",
                    "process_type": "ETCH",
                    "task_type": "COMPARISON",
                    "condition_a": {
                      "pressure": { "value": 11.0, "unit": "mTorr", "status": "VALID" },
                      "source_power": { "value": 501.0, "unit": "W", "status": "VALID" },
                      "bias_power": { "value": 201.0, "unit": "W", "status": "VALID" }
                    },
                    "condition_b": {
                      "pressure": { "value": 6.0, "unit": "mTorr", "status": "VALID" },
                      "source_power": { "value": 201.0, "unit": "W", "status": "VALID" },
                      "bias_power": { "value": 401.0, "unit": "W", "status": "VALID" }
                    }
                  }
                }
                """);

        assertThat(response.requestId()).isEqualTo("req-001");
        assertThat(response.taskType()).isEqualTo("COMPARISON");
        assertThat(response.conditionA().pressure().value()).isEqualTo(11.0);
        assertThat(response.conditionB().biasPower().unit()).isEqualTo("W");
    }

    @Test
    void parseResponse_직접응답도_해석한다() {
        ExtractedParameterData response = extractClient.parseResponse("""
                {
                  "request_id": "req-002",
                  "validation_status": "VALID",
                  "process_type": "ETCH",
                  "task_type": "PREDICTION",
                  "process_params": {
                    "pressure": { "value": 50.0, "unit": "mTorr", "status": "VALID" },
                    "source_power": { "value": 800.0, "unit": "W", "status": "VALID" },
                    "bias_power": { "value": 100.0, "unit": "W", "status": "VALID" }
                  }
                }
                """);

        assertThat(response.requestId()).isEqualTo("req-002");
        assertThat(response.processParams().sourcePower().value()).isEqualTo(800.0);
    }
}
