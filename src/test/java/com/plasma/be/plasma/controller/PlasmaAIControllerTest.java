package com.plasma.be.plasma.controller;

import com.plasma.be.plasma.client.PlasmaAIClient;
import com.plasma.be.plasma.client.dto.PlasmaExtractResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlasmaAIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlasmaAIClient plasmaAIClient;

    // ── 정상 케이스 ──────────────────────────────────────────────────────────

    @Test
    void extractParameters_성공() throws Exception {
        when(plasmaAIClient.extractParameters(anyString(), anyString()))
                .thenReturn(validAiResponse());

        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "userInput": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 조건에서 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.processType").value("ETCH"))
                .andExpect(jsonPath("$.taskType").value("PREDICTION"))
                .andExpect(jsonPath("$.processParams.pressure.value").value(50.0))
                .andExpect(jsonPath("$.processParams.pressure.unit").value("mTorr"))
                .andExpect(jsonPath("$.processParams.pressure.status").value("VALID"))
                .andExpect(jsonPath("$.processParams.sourcePower.value").value(800.0))
                .andExpect(jsonPath("$.processParams.biasPower.value").value(100.0))
                .andExpect(jsonPath("$.currentOutputs").doesNotExist());
    }

    @Test
    void extractParameters_성공_currentOutputs포함() throws Exception {
        PlasmaExtractResponse aiResponse = new PlasmaExtractResponse(
                "req-001", "VALID", "ETCH", "PREDICTION",
                validProcessParams(),
                new PlasmaExtractResponse.CurrentOutputs(
                        new PlasmaExtractResponse.ValueWithUnit(200.0, "Å/min")
                )
        );
        when(plasmaAIClient.extractParameters(anyString(), anyString())).thenReturn(aiResponse);

        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "userInput": "현재 ER이 200Å/min인데 최적화할 수 있어?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOutputs.etchRate.value").value(200.0))
                .andExpect(jsonPath("$.currentOutputs.etchRate.unit").value("Å/min"));
    }

    // ── 입력 검증 실패 ────────────────────────────────────────────────────────

    @Test
    void extractParameters_sessionId_누락시_400() throws Exception {
        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userInput": "압력 50mTorr 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sessionId is required."));
    }

    @Test
    void extractParameters_userInput_누락시_400() throws Exception {
        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userInput is required."));
    }

    @Test
    void extractParameters_sessionId_공백시_400() throws Exception {
        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "   ",
                                  "userInput": "압력 50mTorr 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sessionId is required."));
    }

    // ── AI 서버 검증 실패 ─────────────────────────────────────────────────────

    @Test
    void extractParameters_파라미터_추출_실패시_400() throws Exception {
        PlasmaExtractResponse aiResponse = new PlasmaExtractResponse(
                "req-001", "INVALID_FIELD", "ETCH", "PREDICTION",
                new PlasmaExtractResponse.ProcessParams(
                        new PlasmaExtractResponse.ValidatedValueWithUnit(null, null, "MISSING"),
                        new PlasmaExtractResponse.ValidatedValueWithUnit(800.0, "W", "VALID"),
                        new PlasmaExtractResponse.ValidatedValueWithUnit(100.0, "W", "VALID")
                ),
                null
        );
        when(plasmaAIClient.extractParameters(anyString(), anyString())).thenReturn(aiResponse);

        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "userInput": "소스 파워 800W 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void extractParameters_지원하지않는_공정이면_400() throws Exception {
        PlasmaExtractResponse aiResponse = new PlasmaExtractResponse(
                "req-001", "UNSUPPORTED", "UNKNOWN", "UNSUPPORTED",
                null, null
        );
        when(plasmaAIClient.extractParameters(anyString(), anyString())).thenReturn(aiResponse);

        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "userInput": "날씨 알려줘"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ── AI 서버 통신 오류 ──────────────────────────────────────────────────────

    @Test
    void extractParameters_AI서버_연결_실패시_500() throws Exception {
        when(plasmaAIClient.extractParameters(anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(post("/api/plasma/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "userInput": "압력 50mTorr 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists());
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    private PlasmaExtractResponse validAiResponse() {
        return new PlasmaExtractResponse(
                "req-001", "VALID", "ETCH", "PREDICTION",
                validProcessParams(),
                null
        );
    }

    private PlasmaExtractResponse.ProcessParams validProcessParams() {
        return new PlasmaExtractResponse.ProcessParams(
                new PlasmaExtractResponse.ValidatedValueWithUnit(50.0, "mTorr", "VALID"),
                new PlasmaExtractResponse.ValidatedValueWithUnit(800.0, "W", "VALID"),
                new PlasmaExtractResponse.ValidatedValueWithUnit(100.0, "W", "VALID")
        );
    }
}
