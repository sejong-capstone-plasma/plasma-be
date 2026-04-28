package com.plasma.be.optimize.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plasma.be.optimize.dto.OptimizeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizeClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OptimizeClient optimizeClient = new OptimizeClient(
            RestClient.builder().baseUrl("http://localhost:8000").build()
    );

    @Test
    void buildBody_스네이크케이스_요청_구조를_생성한다() {
        ObjectNode processParams = objectMapper.createObjectNode();
        processParams.putObject("pressure")
                .put("value", 50.0)
                .put("unit", "mTorr");
        processParams.putObject("source_power")
                .put("value", 800.0)
                .put("unit", "W");
        processParams.putObject("bias_power")
                .put("value", 100.0)
                .put("unit", "W");

        ObjectNode currentOutputs = objectMapper.createObjectNode();
        currentOutputs.putObject("etch_rate")
                .put("value", 120.0)
                .put("unit", "nm/min");

        ObjectNode targetOutputs = objectMapper.createObjectNode();
        targetOutputs.putObject("etch_rate")
                .put("value", 150.0)
                .put("unit", "nm/min");

        OptimizeRequest request = new OptimizeRequest(
                "현재 조건에서 식각률을 더 높이도록 최적화해줘",
                "ETCH",
                processParams,
                currentOutputs,
                targetOutputs
        );

        ObjectNode body = optimizeClient.buildBody(request);

        assertThat(body.get("request_id").asText()).isNotBlank();
        assertThat(body.get("original_user_input").asText()).isEqualTo("현재 조건에서 식각률을 더 높이도록 최적화해줘");
        assertThat(body.get("process_type").asText()).isEqualTo("ETCH");
        assertThat(body.path("process_params").path("pressure").path("value").asDouble()).isEqualTo(50.0);
        assertThat(body.path("current_outputs").path("etch_rate").path("unit").asText()).isEqualTo("nm/min");
        assertThat(body.path("target_outputs").path("etch_rate").path("value").asDouble()).isEqualTo(150.0);
    }
}
