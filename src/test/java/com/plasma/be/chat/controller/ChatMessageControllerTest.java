package com.plasma.be.chat.controller;

import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.chat.repository.ChatSessionRepository;
import com.plasma.be.compare.client.CompareClient;
import com.plasma.be.compare.client.dto.ComparisonPipelineAiResponse;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import com.plasma.be.optimize.client.OptimizeClient;
import com.plasma.be.optimize.client.ParameterImpactClient;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.client.dto.ParameterImpactResponse;
import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import com.plasma.be.plasma.entity.PlasmaDistribution;
import com.plasma.be.plasma.service.PlasmaDistributionService;
import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.question.client.QuestionClient;
import com.plasma.be.question.client.dto.QuestionAnswerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private MessageValidationSnapshotRepository snapshotRepository;

    @MockitoBean
    private ExtractClient extractClient;

    @MockitoBean
    private PredictClient predictClient;

    @MockitoBean
    private OptimizeClient optimizeClient;

    @MockitoBean
    private CompareClient compareClient;

    @MockitoBean
    private QuestionClient questionClient;

    @MockitoBean
    private ParameterImpactClient parameterImpactClient;

    @MockitoBean
    private PlasmaDistributionService plasmaDistributionService;

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(validAiResponse());
        when(predictClient.requestPredictPipeline(anyString(), any(), any(), anyString())).thenReturn(validPredictionResponse());
        when(optimizeClient.requestOptimizePipeline(any())).thenReturn(validOptimizationResponse());
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(validComparisonResponse());
        when(questionClient.requestAnswer(anyString(), any())).thenReturn(aiQuestionAnswerResponse());
        when(parameterImpactClient.requestParameterImpact(any(), any())).thenReturn(validParameterImpactResponse());
        when(plasmaDistributionService.findClosest(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(java.util.Optional.of(validPlasmaDistribution()));
        when(plasmaDistributionService.toResponse(any(PlasmaDistribution.class))).thenReturn(validPlasmaDistributionResponse());
    }

    @Test
    void createMessage_성공() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");

        mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "role": "USER",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("VALID"))
                .andExpect(jsonPath("$.validations[0].parameters[0].key").value("pressure"))
                .andExpect(jsonPath("$.validations[0].parameters[0].value").value(10.0));
    }

    @Test
    void createMessage_INVALID_FIELD도_200으로_내려준다() throws Exception {
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(invalidAiResponse());

        mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession("browser-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "inputText": "압력은 모르겠고 소스 파워 500W, 바이어스 파워 100W로 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.validations[0].allValid").value(false))
                .andExpect(jsonPath("$.validations[0].parameters[0].status").value("MISSING"));
    }

    @Test
    void validateParameters_재검증_가능() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(invalidAiResponse());
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(validAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "inputText": "압력은 모르겠고 소스 파워 500W, 바이어스 파워 100W로 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations", messageId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parameters": [
                                    { "key": "pressure", "value": 10.0, "unit": "mTorr" },
                                    { "key": "source_power", "value": 500.0, "unit": "W" },
                                    { "key": "bias_power", "value": 100.0, "unit": "W" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("USER_CORRECTION"))
                .andExpect(jsonPath("$.allValid").value(true));
    }

    @Test
    void 재검증으로_allValid가_되어도_confirm전에는_confirmed가_false다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(invalidAiResponse());
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(validAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "inputText": "압력은 몰라. 소스 파워 500W, 바이어스 파워 100W로 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validations[0].confirmed").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations", messageId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parameters": [
                                    { "key": "pressure", "value": 10.0, "unit": "mTorr" },
                                    { "key": "source_power", "value": 500.0, "unit": "W" },
                                    { "key": "bias_power", "value": 100.0, "unit": "W" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allValid").value(true))
                .andExpect(jsonPath("$.confirmed").value(false));

        mockMvc.perform(get("/api/chat/messages/sessions/session-001").session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validations.length()").value(2))
                .andExpect(jsonPath("$[0].validations[0].confirmed").value(false))
                .andExpect(jsonPath("$[0].validations[1].allValid").value(true))
                .andExpect(jsonPath("$[0].validations[1].confirmed").value(false));
    }

    @Test
    void confirm후_예측결과가_저장되어_조회응답에도_포함된다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction.prediction_result.ion_flux.value").value(1.23))
                .andExpect(jsonPath("$.plasmaDistribution.matched_pressure").value(10.0))
                .andExpect(jsonPath("$.plasmaDistribution.matched_source_power").value(500.0))
                .andExpect(jsonPath("$.validation.prediction.prediction_result.etch_score.value").value(7.89));

        mockMvc.perform(get("/api/chat/messages/sessions/session-001").session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validations[0].prediction.prediction_result.ion_flux.value").value(1.23))
                .andExpect(jsonPath("$[0].validations[0].prediction.explanation.summary").value("예측 요약"));
    }

    @Test
    void taskType이_비어있으면_confirm에서_요청한_PREDICTION을_실행한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(noTaskTypeAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-no-task-predict",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "PREDICTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction.prediction_result.etch_score.value").value(7.89))
                .andExpect(jsonPath("$.plasmaDistribution.ion_flux").value(2.34))
                .andExpect(jsonPath("$.optimization").isEmpty());
    }

    @Test
    void taskType이_비어있으면_confirm에서_요청한_OPTIMIZATION을_실행한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(noTaskTypeAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-no-task-opt",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "OPTIMIZATION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimization.current.process_params.pressure.value").value(10.0))
                .andExpect(jsonPath("$.optimization.current.prediction_result.etch_score.value").value(7.89))
                .andExpect(jsonPath("$.optimization.current.plasmaDistribution.matched_bias_power").value(100.0))
                .andExpect(jsonPath("$.optimization.candidates.length()").value(3))
                .andExpect(jsonPath("$.optimization.candidates[0].candidate_id").value(2))
                .andExpect(jsonPath("$.optimization.candidates[0].prediction_result.etch_score.value").value(9.1))
                .andExpect(jsonPath("$.optimization.candidates[0].plasmaDistribution.avg_energy").value(45.6))
                .andExpect(jsonPath("$.prediction").isEmpty());
    }

    @Test
    void taskType이_PREDICTION이어도_confirm의_requestedTaskType_OPTIMIZATION을_우선한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-prediction-override-opt",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "OPTIMIZATION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.taskType").value("PREDICTION"))
                .andExpect(jsonPath("$.optimization.current.process_params.pressure.value").value(10.0))
                .andExpect(jsonPath("$.optimization.candidates.length()").value(3))
                .andExpect(jsonPath("$.prediction").isEmpty());
    }

    @Test
    void prediction후_같은_validation에_optimization을_다시_요청할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-prediction-then-optimization",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "PREDICTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prediction.prediction_result.etch_score.value").value(7.89))
                .andExpect(jsonPath("$.optimization").isEmpty());

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "OPTIMIZATION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimization.current.process_params.pressure.value").value(10.0))
                .andExpect(jsonPath("$.optimization.candidates.length()").value(3))
                .andExpect(jsonPath("$.prediction").isEmpty());
    }

    @Test
    void taskType이_비어있고_confirm요청에도_선택이_없으면_400이다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(noTaskTypeAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-no-task-missing",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("requestedTaskType is required when taskType is not inferred."));
    }

    @Test
    void 재검증결과_taskType이_UNSUPPORTED여도_기존_PREDICTION을_유지하고_confirm에서_예측을_실행한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(invalidAiResponse());
        when(extractClient.requestValidation(any(), any(), any(), any())).thenReturn(unsupportedButAllParamsValidResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-unsupported-after-correction",
                                  "inputText": "압력은 모르겠고 소스 파워 500W, 바이어스 파워 100W로 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");

        String validationBody = mockMvc.perform(post("/api/chat/messages/{messageId}/validations", messageId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parameters": [
                                    { "key": "pressure", "value": 10.0, "unit": "mTorr" },
                                    { "key": "source_power", "value": 500.0, "unit": "W" },
                                    { "key": "bias_power", "value": 100.0, "unit": "W" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("PREDICTION"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long validationId = JsonTestHelper.readLong(validationBody, "validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.taskType").value("PREDICTION"))
                .andExpect(jsonPath("$.prediction.prediction_result.etch_score.value").value(7.89));
    }

    @Test
    void taskType이_UNSUPPORTED면_confirm의_requestedTaskType을_우선한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(unsupportedAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-unsupported-confirm-request",
                                  "inputText": "조건은 맞는데 어떤 작업인지 애매한 요청"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "PREDICTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.taskType").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.prediction.prediction_result.etch_score.value").value(7.89))
                .andExpect(jsonPath("$.plasmaDistribution.matched_pressure").value(10.0));

        verify(predictClient).requestPredictPipeline(eq("ETCH"), any(), any(), anyString());
    }

    @Test
    void taskType이_UNSUPPORTED면_confirm의_requestedTaskType_OPTIMIZATION을_우선한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(unsupportedAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-unsupported-confirm-optimization",
                                  "inputText": "조건은 맞는데 어떤 작업인지 애매한 요청"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedTaskType": "OPTIMIZATION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.taskType").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.optimization.current.process_params.source_power.value").value(500.0))
                .andExpect(jsonPath("$.optimization.current.plasmaDistribution.matched_source_power").value(500.0))
                .andExpect(jsonPath("$.optimization.candidates.length()").value(3))
                .andExpect(jsonPath("$.prediction").isEmpty());
    }

    @Test
    void confirm후_OPTIMIZATION_결과를_함께_반환한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(optimizationAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-optimization",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 조건에서 최적화해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimization.current.process_params.bias_power.value").value(100.0))
                .andExpect(jsonPath("$.optimization.current.plasmaDistribution.matched_pressure").value(10.0))
                .andExpect(jsonPath("$.optimization.candidates[0].candidate_id").value(2))
                .andExpect(jsonPath("$.optimization.candidates[0].plasmaDistribution.ion_flux").value(2.34))
                .andExpect(jsonPath("$.optimization.candidates[2].candidate_id").value(1))
                .andExpect(jsonPath("$.prediction").isEmpty())
                .andExpect(jsonPath("$.comparison").isEmpty());
    }

    @Test
    void confirm후_COMPARISON_직접_두조건을_비교할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(directComparisonAiResponse());
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> comparisonFromParams(invocation.getArgument(1), invocation.getArgument(3)));

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-inline",
                                  "inputText": "압력 10 소스 파워 500 바이어스 파워 200 일 때랑 압력 5 소스 파워 200 바이어스 파워 400일 때를 비교해줘."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("VALID"))
                .andExpect(jsonPath("$.validations[0].allValid").value(true))
                .andExpect(jsonPath("$.validations[0].parameters.length()").value(0))
                .andExpect(jsonPath("$.validations[0].conditionA.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.validations[0].conditionB.parameters[0].value").value(6.0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.left.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.comparison.right.parameters[0].value").value(6.0))
                .andExpect(jsonPath("$.comparison.right.parameters[2].value").value(400.0))
                .andExpect(jsonPath("$.comparison.right.prediction.prediction_result.etch_score.value").value(606.0));
    }

    @Test
    void COMPARISON_히스토리가_없어_조건이_불완전하면_confirm을_막고_재입력으로_완성할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(incompleteComparisonAiResponse());
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> comparisonFromParams(invocation.getArgument(1), invocation.getArgument(3)));

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-missing",
                                  "inputText": "압력 8이랑 10일 때 비교해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.validations[0].allValid").value(false))
                .andExpect(jsonPath("$.validations[0].parameters.length()").value(2))
                .andExpect(jsonPath("$.validations[0].parameters[0].key").value("source_power"))
                .andExpect(jsonPath("$.validations[0].parameters[1].key").value("bias_power"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isBadRequest());

        String correctedBody = mockMvc.perform(post("/api/chat/messages/{messageId}/validations", messageId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parameters": [
                                    { "key": "source_power", "value": 500.0, "unit": "W" },
                                    { "key": "bias_power", "value": 100.0, "unit": "W" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("COMPARISON"))
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.allValid").value(true))
                .andExpect(jsonPath("$.conditionA.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.conditionB.parameters[2].value").value(100.0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long correctedValidationId = JsonTestHelper.readLong(correctedBody, "validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, correctedValidationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.left.parameters[0].value").value(8.0))
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.comparison.right.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.comparison.right.parameters[2].value").value(100.0));
    }

    @Test
    void COMPARISON_confirm에서_조건payload로_누락값을_보완할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(incompleteComparisonAiResponse());
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> comparisonFromParams(invocation.getArgument(1), invocation.getArgument(3)));

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-confirm-payload",
                                  "inputText": "압력 8이랑 10일 때 비교해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.validations[0].allValid").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conditionA": {
                                    "parameters": [
                                      { "key": "source_power", "value": 500.0, "unit": "W" },
                                      { "key": "bias_power", "value": 100.0, "unit": "W" }
                                    ]
                                  },
                                  "conditionB": {
                                    "parameters": [
                                      { "key": "source_power", "value": 500.0, "unit": "W" },
                                      { "key": "bias_power", "value": 100.0, "unit": "W" }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.sourceType").value("USER_CORRECTION"))
                .andExpect(jsonPath("$.validation.taskType").value("COMPARISON"))
                .andExpect(jsonPath("$.validation.allValid").value(true))
                .andExpect(jsonPath("$.comparison.left.parameters[0].value").value(8.0))
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.comparison.left.parameters[2].value").value(100.0))
                .andExpect(jsonPath("$.comparison.right.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.comparison.right.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.comparison.right.parameters[2].value").value(100.0));
    }

    @Test
    void COMPARISON_범위초과값은_OUT_OF_RANGE로_반환한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(outOfRangeComparisonAiResponse());

        mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-out-range",
                                  "inputText": "압력 10, 소스 200, 바이어스 150이랑 압력 4, 소스 600, 바이어스 200 비교해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.validations[0].allValid").value(false))
                .andExpect(jsonPath("$.validations[0].parameters.length()").value(1))
                .andExpect(jsonPath("$.validations[0].parameters[0].key").value("source_power"))
                .andExpect(jsonPath("$.validations[0].parameters[0].status").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.validations[0].parameters[0].value").value(600.0))
                .andExpect(jsonPath("$.validations[0].conditionB.parameters[1].status").value("OUT_OF_RANGE"));
    }

    @Test
    void confirm후_COMPARISON_그조건과_새조건을_비교할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> comparisonFromParams(invocation.getArgument(1), invocation.getArgument(3)));

        String baseBody = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-history",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long baseMessageId = JsonTestHelper.readLong(baseBody, "messageId");
        long baseValidationId = JsonTestHelper.readLong(baseBody, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", baseMessageId, baseValidationId)
                        .session(browserSession))
                .andExpect(status().isOk());

        when(extractClient.requestExtraction(anyString(), any())).thenReturn(latestAndNewComparisonAiResponse());

        String compareBody = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-history",
                                  "inputText": "그 조건이랑 압력 10 소스 파워 500 바이어스 파워 200일 때를 비교해줘."
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(compareBody, "messageId");
        long validationId = JsonTestHelper.readLong(compareBody, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.left.label").value("left"))
                .andExpect(jsonPath("$.comparison.left.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.comparison.right.label").value("right"))
                .andExpect(jsonPath("$.comparison.right.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.comparison.right.prediction.prediction_result.etch_score.value").value(710.0));
    }

    @Test
    void confirm후_COMPARISON_그조건의_변화량_비교를_할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any()))
                .thenReturn(validAiResponseWithSource400(), patchedHistoryComparisonAiResponse());
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> comparisonFromParams(invocation.getArgument(1), invocation.getArgument(3)));

        String baseBody = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-patch",
                                  "inputText": "압력 10mTorr, 소스 파워 500W, 바이어스 파워 100W 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long baseMessageId = JsonTestHelper.readLong(baseBody, "messageId");
        long baseValidationId = JsonTestHelper.readLong(baseBody, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", baseMessageId, baseValidationId)
                        .session(browserSession))
                .andExpect(status().isOk());

        String compareBody = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-patch",
                                  "inputText": "그 조건에서 소스파워가 100 높아졌을 때를 비교해줘."
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(compareBody, "messageId");
        long validationId = JsonTestHelper.readLong(compareBody, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparison.left.label").value("left"))
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(400.0))
                .andExpect(jsonPath("$.comparison.right.label").value("right"))
                .andExpect(jsonPath("$.comparison.right.parameters[1].value").value(500.0))
                .andExpect(jsonPath("$.comparison.difference.etchScoreDelta").value(100.0));
    }

    @Test
    void confirm후_QUESTION_시스템질문은_로컬응답을_반환한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(questionAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-question-system",
                                  "inputText": "내가 입력할 수 있는 압력 범위가 뭐야?"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.answerSource").value("SYSTEM"))
                .andExpect(jsonPath("$.question.answerText").value(org.hamcrest.Matchers.containsString("별도로 강제하지 않습니다")));

        verify(questionClient, never()).requestAnswer(anyString(), any());
    }

    @Test
    void confirm후_QUESTION_일반질문은_AI응답을_반환한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(questionAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-question-ai",
                                  "inputText": "ion flux가 뭐야?"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long messageId = JsonTestHelper.readLong(body, "messageId");
        long validationId = JsonTestHelper.readLong(body, "validations[0].validationId");

        mockMvc.perform(post("/api/chat/messages/{messageId}/validations/{validationId}/confirm", messageId, validationId)
                        .session(browserSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.answerSource").value("AI"))
                .andExpect(jsonPath("$.question.answerText").value("ion flux는 플라즈마 내 이온의 흐름을 나타내는 지표입니다."));

        verify(questionClient).requestAnswer(anyString(), any());
    }

    @Test
    void getSessionList_및_getMessageList() throws Exception {
        MockHttpSession browserA = browserSession("browser-a");
        MockHttpSession browserB = browserSession("browser-b");

        createMessage(browserA, "session-001", "첫 번째 세션 질문");
        createMessage(browserA, "session-001", "첫 번째 세션 추가 질문");
        createMessage(browserA, "session-002", "두 번째 세션 질문");
        createMessage(browserB, "session-003", "다른 브라우저 세션 질문");

        mockMvc.perform(get("/api/chat/messages/sessions").session(browserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("session-002"))
                .andExpect(jsonPath("$[0].messageCount").value(1))
                .andExpect(jsonPath("$[1].sessionId").value("session-001"))
                .andExpect(jsonPath("$[1].messageCount").value(2));

        mockMvc.perform(get("/api/chat/messages/sessions/session-001").session(browserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("session-001"))
                .andExpect(jsonPath("$[0].inputText").value("첫 번째 세션 질문"))
                .andExpect(jsonPath("$[0].validations[0].validationStatus").value("VALID"))
                .andExpect(jsonPath("$[1].inputText").value("첫 번째 세션 추가 질문"));
    }

    @Test
    void endSession_세션_숨김_처리() throws Exception {
        MockHttpSession browserA = browserSession("browser-a");

        createMessage(browserA, "session-001", "첫 번째 세션 질문");
        createMessage(browserA, "session-002", "두 번째 세션 질문");

        mockMvc.perform(post("/api/chat/messages/sessions/session-001/end").session(browserA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chat/messages/sessions").session(browserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value("session-002"));

        mockMvc.perform(get("/api/chat/messages/sessions/session-001").session(browserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inputText").value("첫 번째 세션 질문"));
    }

    @Test
    void 다른_브라우저의_sessionId로_메시지_조회시_404() throws Exception {
        MockHttpSession ownerBrowser = browserSession("browser-a");
        MockHttpSession foreignBrowser = browserSession("browser-b");

        createMessage(ownerBrowser, "session-001", "첫 번째 세션 질문");

        mockMvc.perform(get("/api/chat/messages/sessions/session-001").session(foreignBrowser))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session is not accessible from the current browser."));
    }

    @Test
    void 다른_브라우저의_sessionId로_메시지_저장시_404() throws Exception {
        MockHttpSession ownerBrowser = browserSession("browser-a");
        MockHttpSession foreignBrowser = browserSession("browser-b");

        createMessage(ownerBrowser, "session-001", "첫 번째 세션 질문");

        mockMvc.perform(post("/api/chat/messages")
                        .session(foreignBrowser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001",
                                  "inputText": "가로채기 시도"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session is not accessible from the current browser."));
    }

    private void createMessage(MockHttpSession browserSession, String sessionId, String inputText) throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "inputText": "%s"
                                }
                                """.formatted(sessionId, inputText)))
                .andExpect(status().isOk());
    }

    private MockHttpSession browserSession(String id) {
        return new MockHttpSession(null, id);
    }

    private ExtractedParameterData validAiResponse() {
        return new ExtractedParameterData(
                "req-001", "VALID", "ETCH", "PREDICTION",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null,
                null,
                null
        );
    }

    private ExtractedParameterData validAiResponseWithSource400() {
        return new ExtractedParameterData(
                "req-001-alt", "VALID", "ETCH", "PREDICTION",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(400.0, "W", "VALID"),
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
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null,
                null,
                null
        );
    }

    private PredictPipelineResponse validPredictionResponse() {
        return new PredictPipelineResponse(
                "predict-001",
                "ETCH",
                new PredictPipelineResponse.PredictionResult(
                        new PredictPipelineResponse.ValueWithUnit(1.23, "a.u."),
                        new PredictPipelineResponse.ValueWithUnit(4.56, "eV"),
                        new PredictPipelineResponse.ValueWithUnit(7.89, "score")
                ),
                new PredictPipelineResponse.Explanation("예측 요약", java.util.List.of("설명1", "설명2"))
        );
    }

    private ExtractedParameterData optimizationAiResponse() {
        return new ExtractedParameterData(
                "req-opt-001", "VALID", "ETCH", "OPTIMIZATION",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                new ExtractedParameterData.CurrentOutputs(
                        new ExtractedParameterData.ValueWithUnit(120.0, "nm/min")
                ),
                null,
                null
        );
    }

    private ExtractedParameterData directComparisonAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-001", "VALID", "ETCH", "COMPARISON",
                null,
                null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(200.0, "W", "VALID")
                ),
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(6.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(200.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(400.0, "W", "VALID")
                )
        );
    }

    private ExtractedParameterData incompleteComparisonAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-incomplete", "INVALID_FIELD", "ETCH", "COMPARISON",
                null,
                null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(8.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(null, "W", "MISSING"),
                        new ExtractedParameterData.ValidatedParam(null, "W", "MISSING")
                ),
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(null, "W", "MISSING"),
                        new ExtractedParameterData.ValidatedParam(null, "W", "MISSING")
                )
        );
    }

    private ExtractedParameterData outOfRangeComparisonAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-out-range", "VALID", "ETCH", "COMPARISON",
                null,
                null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(200.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(110.0, "W", "VALID")
                ),
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(4.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(600.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(200.0, "W", "VALID")
                )
        );
    }

    private ExtractedParameterData latestAndNewComparisonAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-002", "VALID", "ETCH", "COMPARISON",
                null,
                null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(200.0, "W", "VALID")
                )
        );
    }

    private ExtractedParameterData patchedHistoryComparisonAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-patch", "VALID", "ETCH", "COMPARISON",
                null,
                null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(400.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                )
        );
    }

    private ExtractedParameterData comparisonFallbackAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-fallback", "INVALID_FIELD", "ETCH", "COMPARISON",
                null,
                null,
                null,
                null
        );
    }

    private OptimizePipelineResponse validOptimizationResponse() {
        var baselineOutputs = new OptimizePipelineResponse.BaselineOutputs(
                new OptimizePipelineResponse.ValueWithUnit(7.89, "score")
        );
        var candidates = java.util.List.of(
                optimizationCandidate(1, 45.0, 820.0, 95.0, 1.4, 4.9, 7.5),
                optimizationCandidate(2, 42.0, 840.0, 90.0, 1.8, 5.2, 9.1),
                optimizationCandidate(3, 48.0, 810.0, 92.0, 1.6, 5.0, 8.3),
                optimizationCandidate(4, 55.0, 780.0, 110.0, 1.1, 4.2, 6.2)
        );
        var optimizationResult = new OptimizePipelineResponse.OptimizationResult(4, candidates);
        var explanation = new OptimizePipelineResponse.Explanation("최적화 완료", java.util.List.of());
        return new OptimizePipelineResponse("req-opt-001", "ETCH", baselineOutputs, optimizationResult, explanation);
    }

    private OptimizePipelineResponse.OptimizationCandidate optimizationCandidate(int rank,
                                                                                  Double pressure,
                                                                                  Double sourcePower,
                                                                                  Double biasPower,
                                                                                  Double ionFlux,
                                                                                  Double ionEnergy,
                                                                                  Double etchScore) {
        var processParams = new OptimizePipelineResponse.ProcessParams(
                new OptimizePipelineResponse.ValueWithUnit(pressure, "mTorr"),
                new OptimizePipelineResponse.ValueWithUnit(sourcePower, "W"),
                new OptimizePipelineResponse.ValueWithUnit(biasPower, "W")
        );
        var predictionResult = new PredictPipelineResponse.PredictionResult(
                new PredictPipelineResponse.ValueWithUnit(ionFlux, "a.u."),
                new PredictPipelineResponse.ValueWithUnit(ionEnergy, "eV"),
                new PredictPipelineResponse.ValueWithUnit(etchScore, "score")
        );
        return new OptimizePipelineResponse.OptimizationCandidate(rank, processParams, predictionResult, null, etchScore);
    }

    private ComparisonPipelineAiResponse validComparisonResponse() {
        return comparisonFromParams(
                java.util.Map.of("pressure", 10.0, "source_power", 500.0, "bias_power", 100.0),
                java.util.Map.of("pressure", 10.0, "source_power", 500.0, "bias_power", 200.0)
        );
    }

    private ExtractedParameterData questionAiResponse() {
        return new ExtractedParameterData(
                "req-question-001", "VALID", null, "QUESTION",
                null,
                null,
                null,
                null
        );
    }

    private ExtractedParameterData noTaskTypeAiResponse() {
        return new ExtractedParameterData(
                "req-no-task-001", "VALID", "ETCH", null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null,
                null,
                null
        );
    }

    private ExtractedParameterData unsupportedButAllParamsValidResponse() {
        return new ExtractedParameterData(
                "req-unsupported", "UNSUPPORTED", null, null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null,
                null,
                null
        );
    }

    private ExtractedParameterData unsupportedAiResponse() {
        return new ExtractedParameterData(
                "req-unsupported-ai", "UNSUPPORTED", "UNKNOWN", "UNSUPPORTED",
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(10.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(500.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                null,
                null,
                null
        );
    }

    private QuestionAnswerResponse aiQuestionAnswerResponse() {
        return new QuestionAnswerResponse(
                "question-001",
                "ion flux는 플라즈마 내 이온의 흐름을 나타내는 지표입니다.",
                "AI",
                java.util.List.of("plasma-dictionary")
        );
    }

    @SuppressWarnings("unchecked")
    private ComparisonPipelineAiResponse comparisonFromParams(Object rawLeftParams, Object rawRightParams) {
        java.util.Map<String, Double> leftParams = (java.util.Map<String, Double>) rawLeftParams;
        java.util.Map<String, Double> rightParams = (java.util.Map<String, Double>) rawRightParams;

        PredictPipelineResponse.PredictionResult leftResult = predictionResultFromParams(leftParams);
        PredictPipelineResponse.PredictionResult rightResult = predictionResultFromParams(rightParams);

        var conditionA = new ComparisonPipelineAiResponse.ConditionResult(
                buildAiProcessParams(leftParams),
                leftResult,
                new PredictPipelineResponse.Explanation("비교 예측", java.util.List.of())
        );
        var conditionB = new ComparisonPipelineAiResponse.ConditionResult(
                buildAiProcessParams(rightParams),
                rightResult,
                new PredictPipelineResponse.Explanation("비교 예측", java.util.List.of())
        );
        return new ComparisonPipelineAiResponse("cmp-dynamic", "ETCH", conditionA, conditionB);
    }

    private ComparisonPipelineAiResponse.ProcessParams buildAiProcessParams(java.util.Map<String, Double> params) {
        return new ComparisonPipelineAiResponse.ProcessParams(
                new ComparisonPipelineAiResponse.ValueWithUnit(params.getOrDefault("pressure", 0.0), "mTorr"),
                new ComparisonPipelineAiResponse.ValueWithUnit(params.getOrDefault("source_power", 0.0), "W"),
                new ComparisonPipelineAiResponse.ValueWithUnit(params.getOrDefault("bias_power", 0.0), "W")
        );
    }

    private PredictPipelineResponse.PredictionResult predictionResultFromParams(java.util.Map<String, Double> params) {
        double pressure = params.getOrDefault("pressure", 0.0);
        double sourcePower = params.getOrDefault("source_power", 0.0);
        double biasPower = params.getOrDefault("bias_power", 0.0);
        return new PredictPipelineResponse.PredictionResult(
                new PredictPipelineResponse.ValueWithUnit(pressure, "a.u."),
                new PredictPipelineResponse.ValueWithUnit(sourcePower, "eV"),
                new PredictPipelineResponse.ValueWithUnit(pressure + sourcePower + biasPower, "score")
        );
    }

    private ParameterImpactResponse validParameterImpactResponse() {
        var points = java.util.List.of(new ParameterImpactResponse.ImpactPoint(10.0, 7.5));
        return new ParameterImpactResponse("pi-001", "ETCH", points, points, points);
    }

    private PlasmaDistribution validPlasmaDistribution() {
        return PlasmaDistribution.create(
                10.0, 500.0, 100.0,
                2.34, 45.6, 7.8,
                "[1.0, 2.0, 3.0]",
                "[0.1, 0.2, 0.3]"
        );
    }

    private PlasmaDistributionResponse validPlasmaDistributionResponse() {
        return new PlasmaDistributionResponse(
                10.0, 500.0, 100.0,
                2.34, 45.6, 7.8,
                java.util.List.of(1.0, 2.0, 3.0),
                java.util.List.of(0.1, 0.2, 0.3)
        );
    }
}
