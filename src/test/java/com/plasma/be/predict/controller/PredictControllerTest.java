package com.plasma.be.predict.controller;

import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PredictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PredictClient predictClient;

    @Test
    void predictRaw_프론트용_엔드포인트로_호출할_수_있다() throws Exception {
        when(predictClient.requestPredictPipeline(anyString(), any(), any(), anyString()))
                .thenReturn(new PredictPipelineResponse(
                        "predict-001",
                        "ETCH",
                        new PredictPipelineResponse.PredictionResult(
                                new PredictPipelineResponse.ValueWithUnit(1.23, "a.u."),
                                new PredictPipelineResponse.ValueWithUnit(4.56, "eV"),
                                new PredictPipelineResponse.ValueWithUnit(7.89, "score")
                        ),
                        new PredictPipelineResponse.Explanation("예측 요약", List.of("line-1", "line-2"))
                ));

        mockMvc.perform(post("/api/predict/raw")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUserInput": "압력 5mTorr, 소스파워 400W, 바이어스파워 100W로 예측해줘",
                                  "processType": "ETCH",
                                  "pressure":    { "value": 5.0,   "unit": "mTorr" },
                                  "sourcePower": { "value": 400.0, "unit": "W" },
                                  "biasPower":   { "value": 100.0, "unit": "W" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value("predict-001"))
                .andExpect(jsonPath("$.prediction_result.ion_flux.value").value(1.23))
                .andExpect(jsonPath("$.explanation.summary").value("예측 요약"));
    }
}
