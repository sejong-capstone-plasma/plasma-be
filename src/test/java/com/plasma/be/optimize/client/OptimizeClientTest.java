package com.plasma.be.optimize.client;

import com.plasma.be.optimize.dto.OptimizeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizeClientTest {

    private final OptimizeClient optimizeClient = new OptimizeClient(
            RestClient.builder().baseUrl("http://localhost:8000").build()
    );

    @Test
    void buildBody_스네이크케이스_요청_구조를_생성한다() {
        Map<String, Object> processParams = Map.of(
                "pressure", Map.of("value", 50.0, "unit", "mTorr"),
                "source_power", Map.of("value", 800.0, "unit", "W"),
                "bias_power", Map.of("value", 100.0, "unit", "W")
        );
        Map<String, Object> currentOutputs = Map.of(
                "etch_rate", Map.of("value", 120.0, "unit", "nm/min")
        );

        OptimizeRequest request = new OptimizeRequest(
                "현재 조건에서 식각률을 더 높이도록 최적화해줘",
                "ETCH",
                processParams,
                currentOutputs
        );

        Map<String, Object> body = optimizeClient.buildBody(request);

        assertThat(body.get("request_id")).isNotNull();
        assertThat(body.get("original_user_input")).isEqualTo("현재 조건에서 식각률을 더 높이도록 최적화해줘");
        assertThat(body.get("process_type")).isEqualTo("ETCH");
        assertThat(((Map<?, ?>) ((Map<?, ?>) body.get("process_params")).get("pressure")).get("value")).isEqualTo(50.0);
        assertThat(((Map<?, ?>) ((Map<?, ?>) body.get("current_outputs")).get("etch_rate")).get("unit")).isEqualTo("nm/min");
        assertThat(body).doesNotContainKey("target_outputs");
    }
}
