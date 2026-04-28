package com.plasma.be.optimize.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plasma.be.optimize.client.OptimizeClient;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptimizeControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OptimizeClient optimizeClient;

    @InjectMocks
    private OptimizeController optimizeController;

    @Test
    void optimizeRaw_AI응답을_그대로_반환한다() {
        OptimizeRequest request = validRequest();
        OptimizePipelineResponse aiResponse = new OptimizePipelineResponse();
        aiResponse.put("status", "ok");
        aiResponse.put("summary", "optimized");
        when(optimizeClient.requestOptimizePipeline(any())).thenReturn(aiResponse);

        ResponseEntity<OptimizePipelineResponse> response = optimizeController.optimizeRaw(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(aiResponse);
    }

    @Test
    void optimizeRaw_processParams가_비어있으면_예외를_던진다() {
        OptimizeRequest request = new OptimizeRequest(
                "최적화해줘",
                "ETCH",
                objectMapper.createObjectNode(),
                null,
                null
        );

        assertThatThrownBy(() -> optimizeController.optimizeRaw(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processParams must be a non-empty object.");
    }

    private OptimizeRequest validRequest() {
        ObjectNode processParams = objectMapper.createObjectNode();
        processParams.putObject("pressure")
                .put("value", 50.0)
                .put("unit", "mTorr");

        ObjectNode targetOutputs = objectMapper.createObjectNode();
        targetOutputs.putObject("etch_rate")
                .put("value", 150.0)
                .put("unit", "nm/min");

        return new OptimizeRequest(
                "최적화해줘",
                "ETCH",
                processParams,
                null,
                targetOutputs
        );
    }
}
