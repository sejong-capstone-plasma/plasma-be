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
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
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
import static org.mockito.ArgumentMatchers.argThat;
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
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(invalidAiResponse());
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
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(aiResponseWithEmptyMarkers());
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
        when(snapshotRepository.findByMessageMessageIdOrderByAttemptNoAsc(anyLong())).thenReturn(List.of());
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
    void validateCorrection_AI가_UNSUPPORTED를_주더라도_기존_taskType을_유지한다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findByMessageMessageIdOrderByAttemptNoAsc(anyLong()))
                .thenReturn(List.of(validSnapshot()));
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
        assertThat(response.taskType()).isEqualTo("PREDICTION");
        assertThat(response.allValid()).isTrue();
        assertThat(response.parameters()).allMatch(parameter -> "VALID".equals(parameter.status()));
    }

    @Test
    void validateCorrection_기존_taskType이_있으면_AI_재검증에도_그값을_보낸다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findByMessageMessageIdOrderByAttemptNoAsc(anyLong()))
                .thenReturn(List.of(validSnapshot()));
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(validAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParameterValidationRequest request = new ParameterValidationRequest(List.of(
                new ParameterInputRequest("pressure", 50.0, "mTorr"),
                new ParameterInputRequest("source_power", 800.0, "W"),
                new ParameterInputRequest("bias_power", 100.0, "W")
        ));

        extractService.validateCorrection(1L, request);

        verify(extractClient).requestValidation(any(), argThat("PREDICTION"::equals), any(), any());
    }

    @Test
    void validateCorrection_최신_실패스냅샷의_taskType이_null이어도_이전_nonNull_taskType을_사용한다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findByMessageMessageIdOrderByAttemptNoAsc(anyLong()))
                .thenReturn(List.of(unsupportedExtractSnapshot(), failedCorrectionSnapshot()));
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(validAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParameterValidationRequest request = new ParameterValidationRequest(List.of(
                new ParameterInputRequest("pressure", 50.0, "mTorr"),
                new ParameterInputRequest("source_power", 800.0, "W"),
                new ParameterInputRequest("bias_power", 100.0, "W")
        ));

        extractService.validateCorrection(1L, request);

        verify(extractClient).requestValidation(
                argThat("ETCH"::equals),
                argThat("UNSUPPORTED"::equals),
                any(),
                any()
        );
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
        when(extractClient.requestExtraction(anyString(), any())).thenThrow(new RestClientException("Connection refused"));
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> extractService.extractFromMessage(1L))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Connection refused");

        verify(snapshotRepository).save(any(MessageValidationSnapshot.class));
    }

    @Test
    void extractFromMessage_이전_확정된_메시지가_있으면_history를_전달한다() {
        ChatMessage currentMessage = dummyChatMessage();
        ChatMessage priorMessage = new ChatMessage(
                Session.create("session-001", "browser-001", "테스트 세션", LocalDateTime.now()),
                MessageRole.USER, "pressure 5mTorr 예측해줘", LocalDateTime.now()
        );
        MessageValidationSnapshot confirmedSnapshot = MessageValidationSnapshot.create(
                priorMessage, "req-prior", 1, "AI_EXTRACT", "VALID",
                "ETCH", "PREDICTION", null, null, null, null, LocalDateTime.now()
        );
        confirmedSnapshot.storePrediction(
                "predict-prior", "ETCH", null, null, null, null, 75.0, "point",
                "etch score 75.0 point 수준으로 예측됩니다.", null, null
        );
        confirmedSnapshot.markConfirmed();

        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(currentMessage));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(chatMessageRepository.findBySessionSessionIdAndMessageIdLessThanOrderByCreatedAtAsc(anyString(), any()))
                .thenReturn(List.of(priorMessage));
        when(snapshotRepository.findByMessageMessageIdAndConfirmedTrue(any()))
                .thenReturn(List.of(confirmedSnapshot));
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(validAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        extractService.extractFromMessage(1L);

        verify(extractClient).requestExtraction(anyString(), argThat(history ->
                history.size() == 2
                && "user".equals(history.get(0).get("role"))
                && "pressure 5mTorr 예측해줘".equals(history.get(0).get("content"))
                && "assistant".equals(history.get(1).get("role"))
                && "etch score 75.0 point 수준으로 예측됩니다.".equals(history.get(1).get("content"))
        ));
    }

    @Test
    void extractFromMessage_이전_메시지가_없으면_빈_history를_전달한다() {
        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(chatMessageRepository.findBySessionSessionIdAndMessageIdLessThanOrderByCreatedAtAsc(anyString(), any()))
                .thenReturn(List.of());
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(validAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        extractService.extractFromMessage(1L);

        verify(extractClient).requestExtraction(anyString(), argThat(List::isEmpty));
    }

    @Test
    void extractFromMessage_이전_메시지에_확정된_스냅샷이_없으면_user항목만_history에_포함된다() {
        ChatMessage priorMessage = new ChatMessage(
                Session.create("session-001", "browser-001", "테스트 세션", LocalDateTime.now()),
                MessageRole.USER, "pressure 5mTorr 예측해줘", LocalDateTime.now()
        );

        when(chatMessageRepository.findById(anyLong())).thenReturn(Optional.of(dummyChatMessage()));
        when(snapshotRepository.findTopByMessageMessageIdOrderByAttemptNoDesc(anyLong())).thenReturn(Optional.empty());
        when(chatMessageRepository.findBySessionSessionIdAndMessageIdLessThanOrderByCreatedAtAsc(anyString(), any()))
                .thenReturn(List.of(priorMessage));
        when(snapshotRepository.findByMessageMessageIdAndConfirmedTrue(any()))
                .thenReturn(List.of());
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(validAiResponse());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        extractService.extractFromMessage(1L);

        verify(extractClient).requestExtraction(anyString(), argThat(history ->
                history.size() == 1
                && "user".equals(history.get(0).get("role"))
        ));
    }

    @Test
    void storePredictionOutcome_예측결과를_저장하고_응답에_포함한다() {
        MessageValidationSnapshot snapshot = validSnapshot();
        when(snapshotRepository.findByValidationIdAndMessageMessageId(11L, 1L)).thenReturn(Optional.of(snapshot));

        ParameterValidationResponse response = extractService.storePredictionOutcome(1L, 11L, predictionResponse(), null);

        assertThat(response.prediction()).isNotNull();
        assertThat(response.prediction().predictionResult().ionFlux().value()).isEqualTo(1.23);
        assertThat(response.prediction().explanation().details()).containsExactly("line-1", "line-2");
        assertThat(response.predictionError()).isNull();
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
                null,
                null,
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
                null,
                null,
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
                ),
                null,
                null
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
                null,
                null,
                null
        );
    }

    private MessageValidationSnapshot validSnapshot() {
        MessageValidationSnapshot snapshot = MessageValidationSnapshot.create(
                dummyChatMessage(),
                "req-validation",
                1,
                "USER_CORRECTION",
                "VALID",
                "ETCH",
                "PREDICTION",
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("pressure", "Pressure", 50.0, "mTorr", "VALID", 0));
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("source_power", "Source Power", 800.0, "W", "VALID", 1));
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("bias_power", "Bias Power", 100.0, "W", "VALID", 2));
        return snapshot;
    }

    private MessageValidationSnapshot unsupportedExtractSnapshot() {
        MessageValidationSnapshot snapshot = MessageValidationSnapshot.create(
                dummyChatMessage(),
                "req-unsupported",
                1,
                "AI_EXTRACT",
                "UNSUPPORTED",
                "ETCH",
                "UNSUPPORTED",
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("pressure", "Pressure", null, "mTorr", "MISSING", 0));
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("source_power", "Source Power", 800.0, "W", "VALID", 1));
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("bias_power", "Bias Power", 100.0, "W", "VALID", 2));
        return snapshot;
    }

    private MessageValidationSnapshot failedCorrectionSnapshot() {
        MessageValidationSnapshot snapshot = MessageValidationSnapshot.create(
                dummyChatMessage(),
                "req-failed-correction",
                2,
                "USER_CORRECTION",
                "AI_ERROR",
                null,
                null,
                null,
                null,
                null,
                "422 Unprocessable Entity",
                LocalDateTime.now()
        );
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("pressure", "Pressure", 50.0, "mTorr", "AI_ERROR", 0));
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("source_power", "Source Power", 800.0, "W", "AI_ERROR", 1));
        snapshot.addItem(com.plasma.be.extract.entity.MessageValidationParam.create("bias_power", "Bias Power", 100.0, "W", "AI_ERROR", 2));
        return snapshot;
    }

    private PredictPipelineResponse predictionResponse() {
        return new PredictPipelineResponse(
                "predict-001",
                "ETCH",
                new PredictPipelineResponse.PredictionResult(
                        new PredictPipelineResponse.ValueWithUnit(1.23, "a.u."),
                        new PredictPipelineResponse.ValueWithUnit(4.56, "eV"),
                        new PredictPipelineResponse.ValueWithUnit(7.89, "score")
                ),
                new PredictPipelineResponse.Explanation("summary", List.of("line-1", "line-2"))
        );
    }
}
