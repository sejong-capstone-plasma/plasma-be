package com.plasma.be.chat.controller;

import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.chat.repository.ChatSessionRepository;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.repository.MessageValidationSnapshotRepository;
import com.plasma.be.predict.client.PredictClient;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
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

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        when(extractClient.requestExtraction(anyString())).thenReturn(validAiResponse());
        when(predictClient.requestPredictPipeline(anyString(), any(), any(), anyString())).thenReturn(validPredictionResponse());
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
        when(extractClient.requestExtraction(anyString())).thenReturn(invalidAiResponse());

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
        when(extractClient.requestExtraction(anyString())).thenReturn(invalidAiResponse());
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
        when(extractClient.requestExtraction(anyString())).thenReturn(invalidAiResponse());
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
}
