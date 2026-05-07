package com.plasma.be.chat.controller;

import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.chat.repository.ChatSessionRepository;
import com.plasma.be.compare.client.CompareClient;
import com.plasma.be.compare.dto.ComparisonResponse;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import com.plasma.be.optimize.client.OptimizeClient;
import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
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
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.validations[0].validationStatus").value("VALID"))
                .andExpect(jsonPath("$.validations[0].parameters[0].key").value("pressure"))
                .andExpect(jsonPath("$.validations[0].parameters[0].value").value(50.0));
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
                                  "inputText": "압력은 모르겠고 소스 파워 800W, 바이어스 파워 100W로 예측해줘"
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
                                  "inputText": "압력은 모르겠고 소스 파워 800W, 바이어스 파워 100W로 예측해줘"
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
                                    { "key": "pressure", "value": 50.0, "unit": "mTorr" },
                                    { "key": "source_power", "value": 800.0, "unit": "W" },
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
                                  "inputText": "압력은 몰라. 소스 파워 800W, 바이어스 파워 100W로 예측해줘"
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
                                    { "key": "pressure", "value": 50.0, "unit": "mTorr" },
                                    { "key": "source_power", "value": 800.0, "unit": "W" },
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
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 식각률 예측해줘"
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
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W"
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
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W"
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
                .andExpect(jsonPath("$.optimization.summary").value("optimized"))
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
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W"
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
    void confirm후_OPTIMIZATION_결과를_함께_반환한다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(optimizationAiResponse());

        String body = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-optimization",
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 조건에서 최적화해줘"
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
                .andExpect(jsonPath("$.optimization.summary").value("optimized"))
                .andExpect(jsonPath("$.prediction").isEmpty())
                .andExpect(jsonPath("$.comparison").isEmpty());
    }

    @Test
    void confirm후_COMPARISON_직접_두조건을_비교할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(extractClient.requestExtraction(anyString(), any())).thenReturn(comparisonAiResponse());
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
                .andExpect(jsonPath("$.comparison.right.parameters[0].value").value(5.0))
                .andExpect(jsonPath("$.comparison.right.parameters[2].value").value(400.0))
                .andExpect(jsonPath("$.comparison.right.prediction.prediction_result.etch_score.value").value(605.0));
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
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 식각률 예측해줘"
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

        when(extractClient.requestExtraction(anyString(), any())).thenReturn(comparisonAiResponse());

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
                .andExpect(jsonPath("$.comparison.left.label").value("latest_confirmed"))
                .andExpect(jsonPath("$.comparison.left.parameters[0].value").value(50.0))
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(800.0))
                .andExpect(jsonPath("$.comparison.right.parameters[0].value").value(10.0))
                .andExpect(jsonPath("$.comparison.right.prediction.prediction_result.etch_score.value").value(710.0));
    }

    @Test
    void confirm후_COMPARISON_그조건의_변화량_비교를_할_수_있다() throws Exception {
        MockHttpSession browserSession = browserSession("browser-a");
        when(compareClient.requestComparePipeline(anyString(), any(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> comparisonFromParams(invocation.getArgument(1), invocation.getArgument(3)));

        String baseBody = mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-compare-patch",
                                  "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 식각률 예측해줘"
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

        when(extractClient.requestExtraction(anyString(), any())).thenReturn(comparisonAiResponse());

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
                .andExpect(jsonPath("$.comparison.left.parameters[1].value").value(800.0))
                .andExpect(jsonPath("$.comparison.right.label").value("patched_latest"))
                .andExpect(jsonPath("$.comparison.right.parameters[1].value").value(900.0))
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
                        new ExtractedParameterData.ValidatedParam(50.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
                new ExtractedParameterData.CurrentOutputs(
                        new ExtractedParameterData.ValueWithUnit(120.0, "nm/min")
                )
        );
    }

    private ExtractedParameterData comparisonAiResponse() {
        return new ExtractedParameterData(
                "req-cmp-001", "INVALID_FIELD", "ETCH", "COMPARISON",
                null,
                null
        );
    }

    private OptimizePipelineResponse validOptimizationResponse() {
        return new OptimizePipelineResponse(java.util.Map.of(
                "summary", "optimized",
                "recommendation", java.util.Map.of(
                        "pressure", 45.0,
                        "source_power", 820.0,
                        "bias_power", 95.0
                )
        ));
    }

    private ComparisonResponse validComparisonResponse() {
        return comparisonFromParams(
                java.util.Map.of("pressure", 50.0, "source_power", 800.0, "bias_power", 100.0),
                java.util.Map.of("pressure", 10.0, "source_power", 500.0, "bias_power", 200.0)
        );
    }

    private ExtractedParameterData questionAiResponse() {
        return new ExtractedParameterData(
                "req-question-001", "VALID", null, "QUESTION",
                null,
                null
        );
    }

    private ExtractedParameterData noTaskTypeAiResponse() {
        return new ExtractedParameterData(
                "req-no-task-001", "VALID", "ETCH", null,
                new ExtractedParameterData.ProcessParams(
                        new ExtractedParameterData.ValidatedParam(50.0, "mTorr", "VALID"),
                        new ExtractedParameterData.ValidatedParam(800.0, "W", "VALID"),
                        new ExtractedParameterData.ValidatedParam(100.0, "W", "VALID")
                ),
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
    private ComparisonResponse comparisonFromParams(Object rawLeftParams, Object rawRightParams) {
        java.util.Map<String, Double> leftParams = (java.util.Map<String, Double>) rawLeftParams;
        java.util.Map<String, Double> rightParams = (java.util.Map<String, Double>) rawRightParams;

        PredictPipelineResponse leftPrediction = predictionFromParams(leftParams);
        PredictPipelineResponse rightPrediction = predictionFromParams(rightParams);
        double etchScoreDelta = rightPrediction.predictionResult().etchScore().value()
                - leftPrediction.predictionResult().etchScore().value();

        return new ComparisonResponse(
                new ComparisonResponse.ConditionResult("left", "ETCH", java.util.List.of(), leftPrediction),
                new ComparisonResponse.ConditionResult("right", "ETCH", java.util.List.of(), rightPrediction),
                new ComparisonResponse.Difference(
                        delta(leftPrediction.predictionResult().ionFlux().value(), rightPrediction.predictionResult().ionFlux().value()),
                        "a.u.",
                        delta(leftPrediction.predictionResult().ionEnergy().value(), rightPrediction.predictionResult().ionEnergy().value()),
                        "eV",
                        etchScoreDelta,
                        "score"
                ),
                "비교 완료"
        );
    }

    private PredictPipelineResponse predictionFromParams(java.util.Map<String, Double> params) {
        double pressure = params.getOrDefault("pressure", 0.0);
        double sourcePower = params.getOrDefault("source_power", 0.0);
        double biasPower = params.getOrDefault("bias_power", 0.0);

        return new PredictPipelineResponse(
                "predict-dynamic",
                "ETCH",
                new PredictPipelineResponse.PredictionResult(
                        new PredictPipelineResponse.ValueWithUnit(pressure, "a.u."),
                        new PredictPipelineResponse.ValueWithUnit(sourcePower, "eV"),
                        new PredictPipelineResponse.ValueWithUnit(pressure + sourcePower + biasPower, "score")
                ),
                new PredictPipelineResponse.Explanation("비교 예측", java.util.List.of())
        );
    }

    private double delta(double left, double right) {
        return right - left;
    }
}
