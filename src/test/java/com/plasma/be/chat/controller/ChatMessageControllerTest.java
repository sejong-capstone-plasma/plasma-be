package com.plasma.be.chat.controller;

import com.plasma.be.chat.repository.ChatMessageRepository;
import com.plasma.be.chat.repository.ChatSessionRepository;
import com.plasma.be.extract.client.ExtractClient;
import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.repository.ExtractionResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    private ExtractionResultRepository extractionResultRepository;

    @MockitoBean
    private ExtractClient extractClient;

    @BeforeEach
    void setUp() {
        extractionResultRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        when(extractClient.requestExtraction(anyString())).thenReturn(validAiResponse());
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
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.processType").value("ETCH"))
                .andExpect(jsonPath("$.taskType").value("PREDICTION"))
                .andExpect(jsonPath("$.processParams.pressure.value").value(50.0))
                .andExpect(jsonPath("$.processParams.source_power.value").value(800.0))
                .andExpect(jsonPath("$.processParams.bias_power.value").value(100.0));
    }

    @Test
    void createMessage_inputText_누락시_400() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession("browser-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("inputText is required."));
    }

    @Test
    void createMessage_sessionId_누락시_400() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .session(browserSession("browser-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputText": "압력 50mTorr 식각률 예측해줘"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sessionId is required."));
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
    void endSessions_일괄_숨김_처리() throws Exception {
        MockHttpSession browserA = browserSession("browser-a");
        MockHttpSession browserB = browserSession("browser-b");

        createMessage(browserA, "session-001", "첫 번째 세션 질문");
        createMessage(browserA, "session-002", "두 번째 세션 질문");
        createMessage(browserA, "session-003", "세 번째 세션 질문");
        createMessage(browserB, "session-004", "다른 브라우저 세션 질문");

        mockMvc.perform(post("/api/chat/messages/sessions/end")
                        .session(browserA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionIds": ["session-001", "session-003"]
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chat/messages/sessions").session(browserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value("session-002"));

        mockMvc.perform(get("/api/chat/messages/sessions").session(browserB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value("session-004"));
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

        mockMvc.perform(get("/api/chat/messages/sessions/session-001").session(ownerBrowser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].inputText").value("첫 번째 세션 질문"));
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
}
