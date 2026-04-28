package com.plasma.be.optimize.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizePipelineResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 객체_응답을_유연하게_역직렬화한다() throws Exception {
        OptimizePipelineResponse response = objectMapper.readValue("""
                {
                  "status": "ok",
                  "summary": "optimized"
                }
                """, OptimizePipelineResponse.class);

        assertThat(response.payload()).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) response.payload();
        assertThat(payload.get("status")).isEqualTo("ok");
        assertThat(payload.get("summary")).isEqualTo("optimized");
    }

    @Test
    void 배열_응답도_역직렬화한다() throws Exception {
        OptimizePipelineResponse response = objectMapper.readValue("""
                [
                  { "key": "pressure", "value": 42.0 }
                ]
                """, OptimizePipelineResponse.class);

        assertThat(response.payload()).isInstanceOf(List.class);
        List<?> payload = (List<?>) response.payload();
        assertThat(payload).hasSize(1);
        assertThat(((Map<?, ?>) payload.get(0)).get("key")).isEqualTo("pressure");
    }
}
