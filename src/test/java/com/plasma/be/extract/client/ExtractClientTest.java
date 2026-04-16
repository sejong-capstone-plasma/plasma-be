package com.plasma.be.extract.client;

import com.plasma.be.extract.client.dto.ExtractedParameterData;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractClientTest {

    private final ExtractClient extractClient = new ExtractClient(
            RestClient.builder().baseUrl("http://localhost:8000").build(),
            JsonMapper.shared(),
            "http://localhost:8000",
            130
    );

    @Test
    void parseResponse_스네이크케이스_JSON을_정상_역직렬화한다() {
        String response = """
                {
                  "request_id": "req-001",
                  "validation_status": "VALID",
                  "process_type": "ETCH",
                  "task_type": "PREDICTION",
                  "process_params": {
                    "pressure": {
                      "value": 50.0,
                      "unit": "mTorr",
                      "status": "VALID"
                    },
                    "source_power": {
                      "value": 800.0,
                      "unit": "W",
                      "status": "VALID"
                    },
                    "bias_power": {
                      "value": 100.0,
                      "unit": "W",
                      "status": "VALID"
                    }
                  },
                  "current_outputs": {
                    "etch_rate": {
                      "value": 200.0,
                      "unit": "A/min",
                      "status": "VALID"
                    }
                  }
                }
                """;

        ExtractedParameterData parsed = extractClient.parseResponse(response);

        assertThat(parsed.requestId()).isEqualTo("req-001");
        assertThat(parsed.validationStatus()).isEqualTo("VALID");
        assertThat(parsed.processType()).isEqualTo("ETCH");
        assertThat(parsed.taskType()).isEqualTo("PREDICTION");
        assertThat(parsed.processParams().pressure().value()).isEqualTo(50.0);
        assertThat(parsed.processParams().sourcePower().value()).isEqualTo(800.0);
        assertThat(parsed.processParams().biasPower().value()).isEqualTo(100.0);
        assertThat(parsed.currentOutputs().etchRate().value()).isEqualTo(200.0);
    }

    @Test
    void buildRequestBody_스네이크케이스_필드를_생성한다() {
        String requestBody = extractClient.buildRequestBody("압력 50mTorr 식각률 예측");

        assertThat(requestBody).contains("\"request_id\"");
        assertThat(requestBody).contains("\"user_input\":\"압력 50mTorr 식각률 예측\"");
    }
}
