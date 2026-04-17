package com.plasma.be.chat.controller;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionsEndRequest;
import com.plasma.be.extract.dto.ExtractionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Tag(name = "Chat Messages", description = "채팅 메시지 생성·조회 및 세션 관리 API. 메시지 생성 시 AI 서버를 통해 공정 파라미터를 자동 추출합니다.")
@RequestMapping("/api/chat/messages")
public interface ChatMessageApi {

    @Operation(
            summary = "메시지 생성 및 파라미터 추출",
            description = "사용자의 자연어 입력을 채팅 메시지로 저장한 뒤, AI 서버를 통해 공정 파라미터(pressure, source_power, bias_power)를 추출하여 반환합니다. "
                    + "추출 결과는 ExtractionResult로 DB에도 저장됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "메시지 저장 및 파라미터 추출 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ExtractionResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "requestId": "550e8400-e29b-41d4-a716-446655440000",
                                      "validationStatus": "VALID",
                                      "processType": "ETCH",
                                      "taskType": "PREDICTION",
                                      "processParams": {
                                        "pressure":     { "value": 50.0,  "unit": "mTorr", "status": "VALID" },
                                        "source_power": { "value": 800.0, "unit": "W",     "status": "VALID" },
                                        "bias_power":   { "value": 100.0, "unit": "W",     "status": "VALID" }
                                      },
                                      "currentEr": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 검증 실패 또는 파라미터 추출 검증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "message": "inputText is required." }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "AI 서버 통신 오류",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "message": "AI server error: Connection refused" }
                                    """))
            )
    })
    @PostMapping
    ResponseEntity<ExtractionResponse> createMessage(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "자연어 공정 분석 요청. sessionId와 inputText는 필수입니다.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ChatMessageCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "sessionId": "session-001",
                                      "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 조건에서 식각률 예측해줘"
                                    }
                                    """))
            )
            @RequestBody ChatMessageCreateRequest request,
            HttpSession browserSession
    );

    @Operation(summary = "세션 목록 조회", description = "활성 상태인 채팅 세션 목록을 최근 메시지 순으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "세션 목록 반환 성공")
    @GetMapping("/sessions")
    ResponseEntity<List<ChatSessionSummaryResponse>> getSessionList(HttpSession browserSession);

    @Operation(summary = "세션별 메시지 조회", description = "특정 세션에 속한 모든 채팅 메시지를 생성 시간 순으로 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 목록 반환 성공"),
            @ApiResponse(responseCode = "400", description = "sessionId 누락")
    })
    @GetMapping("/sessions/{sessionId}")
    ResponseEntity<List<ChatMessageSummaryResponse>> getMessageList(
            @Parameter(description = "조회할 세션 ID", example = "session-001")
            @PathVariable String sessionId,
            HttpSession browserSession
    );

    @Operation(summary = "세션 종료", description = "특정 세션을 종료합니다. 종료된 세션은 목록에서 숨겨지지만 메시지는 유지됩니다.")
    @ApiResponse(responseCode = "204", description = "세션 종료 성공")
    @PostMapping("/sessions/{sessionId}/end")
    ResponseEntity<Void> endSession(
            @Parameter(description = "종료할 세션 ID", example = "session-001")
            @PathVariable String sessionId,
            HttpSession browserSession
    );

    @Operation(summary = "세션 일괄 종료", description = "여러 세션을 한 번에 종료합니다.")
    @ApiResponse(responseCode = "204", description = "세션 일괄 종료 성공")
    @PostMapping("/sessions/end")
    ResponseEntity<Void> endSessions(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "종료할 세션 ID 목록",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "sessionIds": ["session-001", "session-003"] }
                                    """))
            )
            @RequestBody(required = false) ChatSessionsEndRequest request,
            HttpSession browserSession
    );
}
