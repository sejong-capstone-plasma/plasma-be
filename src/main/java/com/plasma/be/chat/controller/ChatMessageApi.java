package com.plasma.be.chat.controller;

import com.plasma.be.chat.dto.ChatMessageCreateRequest;
import com.plasma.be.chat.dto.ChatMessageSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionSummaryResponse;
import com.plasma.be.chat.dto.ChatSessionsEndRequest;
import com.plasma.be.extract.dto.ParameterValidationRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import com.plasma.be.predict.dto.ConfirmResponse;
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

@Tag(name = "Chat Messages", description = "채팅 메시지 저장, 파라미터 추출/재검증, 세션 조회 API")
@RequestMapping("/api/chat/messages")
public interface ChatMessageApi {

    @Operation(
            summary = "메시지 생성 및 첫 추출 실행",
            description = "사용자의 자연어 입력을 저장하고 AI 추출 결과를 첫 검증 스냅샷으로 남깁니다. "
                    + "INVALID_FIELD도 200 응답으로 내려 프론트가 재입력 카드를 띄울 수 있게 합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "메시지 저장 및 첫 검증 완료",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ChatMessageSummaryResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "messageId": 10,
                                      "sessionId": "session-001",
                                      "role": "USER",
                                      "inputText": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 조건에서 식각률 예측해줘",
                                      "createdAt": "2026-04-18T15:30:00",
                                      "validations": [
                                        {
                                          "validationId": 21,
                                          "requestId": "550e8400-e29b-41d4-a716-446655440000",
                                          "messageId": 10,
                                          "attemptNo": 1,
                                          "sourceType": "AI_EXTRACT",
                                          "validationStatus": "INVALID_FIELD",
                                          "processType": "ETCH",
                                          "taskType": "PREDICTION",
                                          "parameters": [
                                            { "key": "pressure", "label": "Pressure", "value": null, "unit": "mTorr", "status": "MISSING" },
                                            { "key": "source_power", "label": "Source Power", "value": 800.0, "unit": "W", "status": "VALID" },
                                            { "key": "bias_power", "label": "Bias Power", "value": 100.0, "unit": "W", "status": "VALID" }
                                          ],
                                          "currentEr": null,
                                          "allValid": false,
                                          "confirmed": false,
                                          "failureReason": null,
                                          "createdAt": "2026-04-18T15:30:02"
                                        }
                                      ]
                                    }
                                    """))
            ),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
            @ApiResponse(responseCode = "500", description = "AI 서버 통신 오류")
    })
    @PostMapping
    ResponseEntity<ChatMessageSummaryResponse> createMessage(
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

    @Operation(
            summary = "수정된 파라미터 재검증",
            description = "프론트에서 받은 수정 파라미터를 같은 메시지의 새 검증 시도로 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재검증 완료",
                    content = @Content(schema = @Schema(implementation = ParameterValidationResponse.class))),
            @ApiResponse(responseCode = "400", description = "필수 파라미터 누락 또는 값 오류"),
            @ApiResponse(responseCode = "404", description = "현재 브라우저에서 접근할 수 없는 메시지"),
            @ApiResponse(responseCode = "500", description = "AI 서버 통신 오류")
    })
    @PostMapping("/{messageId}/validations")
    ResponseEntity<ParameterValidationResponse> validateParameters(
            @Parameter(description = "검증 대상 ChatMessage ID", example = "10")
            @PathVariable("messageId") Long messageId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ParameterValidationRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "parameters": [
                                        { "key": "pressure", "value": 50.0, "unit": "mTorr" },
                                        { "key": "source_power", "value": 800.0, "unit": "W" },
                                        { "key": "bias_power", "value": 100.0, "unit": "W" }
                                      ]
                                    }
                                    """))
            )
            @RequestBody ParameterValidationRequest request,
            HttpSession browserSession
    );

    @Operation(summary = "검증 결과 확정 및 예측 실행",
            description = "모든 파라미터가 VALID인 검증 결과를 최종 확정합니다. "
                    + "task_type이 PREDICTION이면 즉시 예측 파이프라인을 실행하고 결과를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확정 성공 (prediction 필드는 PREDICTION 태스크일 때만 채워짐)"),
            @ApiResponse(responseCode = "400", description = "아직 모든 값이 VALID가 아님"),
            @ApiResponse(responseCode = "404", description = "메시지 또는 검증 결과를 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "AI 예측 서버 오류")
    })
    @PostMapping("/{messageId}/validations/{validationId}/confirm")
    ResponseEntity<ConfirmResponse> confirmParameters(
            @PathVariable("messageId") Long messageId,
            @PathVariable("validationId") Long validationId,
            HttpSession browserSession
    );

    @Operation(summary = "세션 목록 조회", description = "활성 상태인 채팅 세션 목록을 최근 메시지 순으로 반환합니다.")
    @ApiResponse(responseCode = "200", description = "세션 목록 반환 성공")
    @GetMapping("/sessions")
    ResponseEntity<List<ChatSessionSummaryResponse>> getSessionList(HttpSession browserSession);

    @Operation(summary = "세션별 메시지 조회", description = "특정 세션에 속한 모든 메시지와 검증 이력을 생성 시간 순으로 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 목록 반환 성공"),
            @ApiResponse(responseCode = "400", description = "sessionId 누락")
    })
    @GetMapping("/sessions/{sessionId}")
    ResponseEntity<List<ChatMessageSummaryResponse>> getMessageList(
            @Parameter(description = "조회할 세션 ID", example = "session-001")
            @PathVariable("sessionId") String sessionId,
            HttpSession browserSession
    );

    @Operation(summary = "세션 종료", description = "특정 세션을 종료합니다. 종료된 세션은 목록에서 숨겨지지만 메시지는 유지됩니다.")
    @ApiResponse(responseCode = "204", description = "세션 종료 성공")
    @PostMapping("/sessions/{sessionId}/end")
    ResponseEntity<Void> endSession(
            @Parameter(description = "종료할 세션 ID", example = "session-001")
            @PathVariable("sessionId") String sessionId,
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
