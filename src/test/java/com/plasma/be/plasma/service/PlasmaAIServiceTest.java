package com.plasma.be.plasma.service;

import com.plasma.be.plasma.client.PlasmaAIClient;
import com.plasma.be.plasma.client.dto.PlasmaExtractResponse;
import com.plasma.be.plasma.dto.ExtractParametersRequest;
import com.plasma.be.plasma.dto.ExtractParametersResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlasmaAIServiceTest {

    @Mock
    private PlasmaAIClient plasmaAIClient;

    @InjectMocks
    private PlasmaAIService plasmaAIService;

    // ── 정상 케이스 ──────────────────────────────────────────────────────────

    @Test
    void extractParameters_성공() {
        when(plasmaAIClient.extractParameters(anyString(), anyString()))
                .thenReturn(validAiResponse());

        ExtractParametersResponse response = plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "압력 50mTorr, 소스 파워 800W 식각률 예측해줘")
        );

        assertThat(response.validationStatus()).isEqualTo("VALID");
        assertThat(response.processType()).isEqualTo("ETCH");
        assertThat(response.taskType()).isEqualTo("PREDICTION");
        assertThat(response.processParams().pressure().value()).isEqualTo(50.0);
        assertThat(response.processParams().pressure().unit()).isEqualTo("mTorr");
        assertThat(response.processParams().sourcePower().value()).isEqualTo(800.0);
        assertThat(response.processParams().biasPower().value()).isEqualTo(100.0);
        assertThat(response.currentOutputs()).isNull();
    }

    @Test
    void extractParameters_성공_currentOutputs포함() {
        PlasmaExtractResponse aiResponse = new PlasmaExtractResponse(
                "req-001", "VALID", "ETCH", "PREDICTION",
                validProcessParams(),
                new PlasmaExtractResponse.CurrentOutputs(
                        new PlasmaExtractResponse.ValueWithUnit(200.0, "Å/min")
                )
        );
        when(plasmaAIClient.extractParameters(anyString(), anyString())).thenReturn(aiResponse);

        ExtractParametersResponse response = plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "현재 ER이 200인데 최적화해줘")
        );

        assertThat(response.currentOutputs()).isNotNull();
        assertThat(response.currentOutputs().etchRate().value()).isEqualTo(200.0);
        assertThat(response.currentOutputs().etchRate().unit()).isEqualTo("Å/min");
    }

    @Test
    void extractParameters_userInput_앞뒤공백_제거후_전달() {
        when(plasmaAIClient.extractParameters(anyString(), anyString()))
                .thenReturn(validAiResponse());

        plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "  압력 50mTorr  ")
        );

        verify(plasmaAIClient).extractParameters(anyString(), anyString() /* "압력 50mTorr" */);
    }

    // ── 입력 검증 실패 ────────────────────────────────────────────────────────

    @Test
    void extractParameters_request가_null이면_예외() {
        assertThatThrownBy(() -> plasmaAIService.extractParameters(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request body is required.");
    }

    @Test
    void extractParameters_sessionId가_blank이면_예외() {
        assertThatThrownBy(() -> plasmaAIService.extractParameters(
                new ExtractParametersRequest("  ", "valid input")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sessionId is required.");
    }

    @Test
    void extractParameters_userInput이_blank이면_예외() {
        assertThatThrownBy(() -> plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userInput is required.");
    }

    // ── AI 서버 검증 실패 ─────────────────────────────────────────────────────

    @Test
    void extractParameters_INVALID_FIELD이면_예외() {
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

        assertThatThrownBy(() -> plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "소스 파워 800W 식각률 예측해줘")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pressure")
                .hasMessageContaining("MISSING");
    }

    @Test
    void extractParameters_UNSUPPORTED이면_예외() {
        PlasmaExtractResponse aiResponse = new PlasmaExtractResponse(
                "req-001", "UNSUPPORTED", "UNKNOWN", "UNSUPPORTED",
                null, null
        );
        when(plasmaAIClient.extractParameters(anyString(), anyString())).thenReturn(aiResponse);

        assertThatThrownBy(() -> plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "날씨 알려줘")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void extractParameters_OUT_OF_RANGE_파라미터_포함시_예외() {
        PlasmaExtractResponse aiResponse = new PlasmaExtractResponse(
                "req-001", "INVALID_FIELD", "ETCH", "PREDICTION",
                new PlasmaExtractResponse.ProcessParams(
                        new PlasmaExtractResponse.ValidatedValueWithUnit(-10.0, "mTorr", "OUT_OF_RANGE"),
                        new PlasmaExtractResponse.ValidatedValueWithUnit(800.0, "W", "VALID"),
                        new PlasmaExtractResponse.ValidatedValueWithUnit(100.0, "W", "VALID")
                ),
                null
        );
        when(plasmaAIClient.extractParameters(anyString(), anyString())).thenReturn(aiResponse);

        assertThatThrownBy(() -> plasmaAIService.extractParameters(
                new ExtractParametersRequest("session-001", "압력 -10mTorr 식각률 예측해줘")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pressure")
                .hasMessageContaining("OUT_OF_RANGE");
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
