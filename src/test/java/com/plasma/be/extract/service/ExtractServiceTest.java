package com.plasma.be.extract.service;

import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.entity.MessageRole;
import com.plasma.be.chat.entity.Session;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ParametersResponse;
import com.plasma.be.extract.entity.Parameters;
import com.plasma.be.extract.repository.ParametersRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractServiceTest {

    @Mock
    private ExtractClient extractClient;

    @Mock
    private ParametersRepository parametersRepository;

    @InjectMocks
    private ExtractService extractService;

    // ── 정상 케이스 ──────────────────────────────────────────────────────────

    @Test
    void extractAndSave_성공() {
        when(extractClient.requestExtraction(anyString())).thenReturn(validAiResponse());
        when(parametersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParametersResponse response = extractService.extractAndSave(dummyChatMessage());

        assertThat(response.requestId()).isEqualTo("req-001");
        assertThat(response.pressureMtorr()).isEqualTo(50.0);
        assertThat(response.sourcePowerW()).isEqualTo(800.0);
        assertThat(response.biasPowerW()).isEqualTo(100.0);
        assertThat(response.currentEr()).isNull();
    }

    @Test
    void extractAndSave_currentEr_포함() {
        ExtractedParameterData aiResponse = new ExtractedParameterData(
                "req-001", "VALID", "ETCH", "PREDICTION",
                validProcessParams(),
                new ExtractedParameterData.CurrentOutputs(
                        new ExtractedParameterData.ValidatedParam(200.0, "Å/min", "VALID")
                )
        );
        when(extractClient.requestExtraction(anyString())).thenReturn(aiResponse);
        when(parametersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParametersResponse response = extractService.extractAndSave(dummyChatMessage());

        assertThat(response.currentEr()).isEqualTo(200.0);
    }

    @Test
    void extractAndSave_결과가_DB에_저장됨() {
        when(extractClient.requestExtraction(anyString())).thenReturn(validAiResponse());
        when(parametersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        extractService.extractAndSave(dummyChatMessage());

        verify(parametersRepository).save(any(Parameters.class));
    }

    // ── validateExtractedData ────────────────────────────────────────────────

    @Test
    void validateExtractedData_UNSUPPORTED이면_예외() {
        ExtractedParameterData data = new ExtractedParameterData(
                "req-001", "UNSUPPORTED", "UNKNOWN", "UNSUPPORTED", null, null
        );

        assertThatThrownBy(() -> extractService.validateExtractedData(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void validateExtractedData_INVALID_FIELD이면_예외() {
        ExtractedParameterData data = new ExtractedParameterData(
                "req-001", "INVALID_FIELD", "ETCH", "PREDICTION",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(null, null, "MISSING"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null
        );

        assertThatThrownBy(() -> extractService.validateExtractedData(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pressure")
                .hasMessageContaining("MISSING");
    }

    @Test
    void validateExtractedData_null이면_예외() {
        assertThatThrownBy(() -> extractService.validateExtractedData(null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── createParameters ────────────────────────────────────────────────────

    @Test
    void createParameters_정상_매핑() {
        ChatMessage message = dummyChatMessage();

        Parameters result = extractService.createParameters(message, validAiResponse());

        assertThat(result.getRequestId()).isEqualTo("req-001");
        assertThat(result.getPressureMtorr()).isEqualTo(50.0);
        assertThat(result.getSourcePowerW()).isEqualTo(800.0);
        assertThat(result.getBiasPowerW()).isEqualTo(100.0);
        assertThat(result.getCurrentEr()).isNull();
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    private ChatMessage dummyChatMessage() {
        Session session = Session.create("session-001", "browser-001", "테스트 세션", LocalDateTime.now());
        return new ChatMessage(session, MessageRole.USER, "압력 50mTorr 식각률 예측해줘", LocalDateTime.now());
    }

    private ExtractedParameterData validAiResponse() {
        return new ExtractedParameterData(
                "req-001", "VALID", "ETCH", "PREDICTION",
                validProcessParams(),
                null
        );
    }

    private ExtractedParameterData.ProcessParams validProcessParams() {
        return new ExtractedParameterData.ProcessParams(
                new ExtractedParameterData.ValidatedParam(50.0, "mTorr", "VALID"),
                new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
        );
    }
}
