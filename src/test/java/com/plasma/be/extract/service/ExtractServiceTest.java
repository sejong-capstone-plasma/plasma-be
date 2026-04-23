package com.plasma.be.extract.service;

import com.plasma.be.chat.entity.ChatMessage;
import com.plasma.be.chat.entity.MessageRole;
import com.plasma.be.chat.entity.Session;
import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ParameterInputRequest;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.extract.entity.MessageValidationSnapshot;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractServiceTest {

    @Mock
    private ExtractClient extractClient;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MessageValidationSnapshotRepository snapshotRepository;

    @InjectMocks
    private ExtractService extractService;

    @Test
    void extractFromMessage_INVALID_FIELD도_응답으로_반환한다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(extractClient.requestExtraction(anyString())).thenReturn(invalidAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParameterValidationResponse response = extractService.extractFromMessage(1L);

        assertThat(response.validationStatus()).isEqualTo("INVALID_FIELD");
        assertThat(response.allValid()).isFalse();
        assertThat(response.parameters()).hasSize(3);
        assertThat(response.parameters().get(0).status()).isEqualTo("MISSING");
        verify(snapshotRepository).save(any(MessageValidationSnapshot.class));
    }

    @Test
    void extractFromMessage_AI의_无표현은_빈값으로_정규화한다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(extractClient.requestExtraction(anyString())).thenReturn(aiResponseWithEmptyMarkers());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParameterValidationResponse response = extractService.extractFromMessage(1L);

        assertThat(response.validationStatus()).isEqualTo("UNKNOWN");
        assertThat(response.processType()).isNull();
        assertThat(response.taskType()).isNull();
        assertThat(response.parameters().get(0).unit()).isEqualTo("mTorr");
        assertThat(response.parameters().get(0).status()).isEqualTo("MISSING");
        assertThat(response.currentEr()).isNull();
    }

    @Test
    void validateCorrection_사용자입력값을_기반으로_재검증한다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(validAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParameterValidationRequest request = new ParameterValidationRequest(List.of(
                new ParameterInputRequest("pressure", 50.0, "mTorr"),
                new ParameterInputRequest("source_power", 800.0, "W"),
                new ParameterInputRequest("bias_power", 100.0, "W")
        ));

        ParameterValidationResponse response = extractService.validateCorrection(1L, request);

        assertThat(response.sourceType()).isEqualTo("USER_CORRECTION");
        assertThat(response.allValid()).isTrue();
        assertThat(response.parameters()).allMatch(parameter -> "VALID".equals(parameter.status()));
    }

    @Test
    void validateCorrection_AI가_UNSUPPORTED를_주더라도_수정값이_모두_VALID면_성공으로_본다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(unsupportedButAllParamsValidResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParameterValidationRequest request = new ParameterValidationRequest(List.of(
                new ParameterInputRequest("pressure", 50.0, "mTorr"),
                new ParameterInputRequest("source_power", 800.0, "W"),
                new ParameterInputRequest("bias_power", 100.0, "W")
        ));

        ParameterValidationResponse response = extractService.validateCorrection(1L, request);

        assertThat(response.sourceType()).isEqualTo("USER_CORRECTION");
        assertThat(response.validationStatus()).isEqualTo("VALID");
        assertThat(response.allValid()).isTrue();
        assertThat(response.parameters()).allMatch(parameter -> "VALID".equals(parameter.status()));
    }

    @Test
    void validateCorrection_파라미터가_빠지면_예외() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        ParameterValidationRequest request = new ParameterValidationRequest(List.of(
                new ParameterInputRequest("pressure", 50.0, "mTorr")
        ));

        assertThatThrownBy(() -> extractService.validateCorrection(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing parameters");
    }

    @Test
    void extractFromMessage_AI오류시_실패스냅샷을_남기고_예외를_던진다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(extractClient.requestExtraction(anyString())).thenThrow(new RestClientException("Connection refused"));
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> extractService.extractFromMessage(1L))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Connection refused");

        verify(snapshotRepository).save(any(MessageValidationSnapshot.class));
    }

    private ChatMessage dummyChatMessage() {
        Session session = Session.create("session-001", "browser-001", "테스트 세션", LocalDateTime.now());
        return new ChatMessage(session, MessageRole.USER, "압력 50mTorr 식각률 예측해줘", LocalDateTime.now());
    }

    private ExtractedParameterData validAiResponse() {
        return new ExtractedParameterData(
                "req-001", "VALID", "ETCH", "PREDICTION",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(50.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null
        );
    }

    private ExtractedParameterData invalidAiResponse() {
        return new ExtractedParameterData(
                "req-002", "INVALID_FIELD", "ETCH", "PREDICTION",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(null, "mTorr", "MISSING"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null
        );
    }

    private ExtractedParameterData aiResponseWithEmptyMarkers() {
        return new ExtractedParameterData(
                "无", "无", "无", "无",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(null, "无", "无"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                new ExtractedParameterData.CurrentOutputs(
                        new ExtractedParameterData.ValueWithUnit(null, "无")
                )
        );
    }

    private ExtractedParameterData unsupportedButAllParamsValidResponse() {
        return new ExtractedParameterData(
                "req-unsupported", "UNSUPPORTED", null, null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(50.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null
        );
    }
}
