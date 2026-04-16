package com.plasma.be.extract.controller;

import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ExtractTestRequest;
import com.plasma.be.extract.dto.ExtractionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Extract Test", description = "AI 파라미터 추출 기능 단독 테스트 API. 채팅 메시지 저장 없이 추출만 수행합니다.")
@RequestMapping("/api/test/extract")
public interface ExtractTestApi {

    @Operation(
            summary = "AI 서버 연결 확인",
            description = "AI 서버(Plasma AI)가 정상적으로 응답하는지 확인합니다. 고정된 테스트 문장으로 추출을 시도합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "AI 서버 연결 정상",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "ok",
                                      "message": "AI server is reachable"
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "AI 서버 연결 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "error",
                                      "message": "AI server error: Connection refused"
                                    }
                                    """))
            )
    })
    @GetMapping("/ping")
    ResponseEntity<?> ping();

    @Operation(
            summary = "AI 서버 raw 응답 확인",
            description = "자연어 입력을 AI 서버에 전송하고 가공 없이 원본 응답(ExtractedParameterData)을 그대로 반환합니다. "
                    + "AI 서버의 실제 응답 형태를 확인할 때 사용합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "AI 서버 원본 응답",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ExtractedParameterData.class))
            ),
            @ApiResponse(responseCode = "500", description = "AI 서버 통신 오류")
    })
    @PostMapping("/raw")
    ResponseEntity<ExtractedParameterData> extractRaw(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "자연어 공정 분석 요청",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ExtractTestRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "userInput": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 조건에서 식각률 예측해줘"
                                    }
                                    """))
            )
            @RequestBody ExtractTestRequest request
    );

    @Operation(
            summary = "파라미터 추출 테스트 (DB 저장 없음)",
            description = "자연어 입력에서 공정 파라미터를 추출하여 ExtractionResponse 형태로 반환합니다. "
                    + "채팅 메시지 저장, ExtractionResult DB 저장 없이 추출과 검증만 수행합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "추출 성공",
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
                    description = "추출 검증 실패 (UNSUPPORTED 또는 INVALID_FIELD)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "message": "Some parameters could not be extracted properly. [pressure: MISSING]" }
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
    ResponseEntity<ExtractionResponse> extract(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "자연어 공정 분석 요청",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ExtractTestRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "userInput": "압력 50mTorr, 소스 파워 800W, 바이어스 파워 100W 조건에서 식각률 예측해줘"
                                    }
                                    """))
            )
            @RequestBody ExtractTestRequest request
    );
}
