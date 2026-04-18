package com.plasma.be.extract.controller;

import com.plasma.be.extract.client.dto.ExtractedParameterData;
import com.plasma.be.extract.dto.ExtractTestRequest;
import com.plasma.be.extract.dto.ParameterValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Tag(name = "Extract Test", description = "AI 파라미터 추출 기능 단독 테스트 API")
@RequestMapping("/api/test/extract")
public interface ExtractTestApi {

    @Operation(summary = "AI 서버 연결 확인", description = "고정된 테스트 문장으로 AI 서버 응답 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI 서버 연결 정상"),
            @ApiResponse(responseCode = "500", description = "AI 서버 연결 실패")
    })
    @GetMapping("/ping")
    ResponseEntity<?> ping();

    @Operation(summary = "AI 서버 raw 응답 확인", description = "자연어 입력을 보내고 원본 응답을 그대로 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI 서버 원본 응답",
                    content = @Content(schema = @Schema(implementation = ExtractedParameterData.class))),
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

    @Operation(summary = "추출 + 저장 테스트", description = "테스트용 메시지를 저장하고 첫 검증 스냅샷까지 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추출 및 저장 성공",
                    content = @Content(schema = @Schema(implementation = ParameterValidationResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
            @ApiResponse(responseCode = "500", description = "AI 서버 통신 오류")
    })
    @PostMapping("/save")
    ResponseEntity<ParameterValidationResponse> extractAndSave(
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

    @Operation(summary = "검증 결과 단건 조회", description = "validationId로 저장된 검증 결과를 조회합니다.")
    @GetMapping("/results/{validationId}")
    ResponseEntity<ParameterValidationResponse> getValidation(
            @Parameter(description = "검증 결과 ID", example = "21")
            @PathVariable("validationId") Long validationId
    );

    @Operation(summary = "메시지별 검증 이력 조회", description = "특정 메시지에 연결된 모든 검증 이력을 조회합니다.")
    @GetMapping("/results/message/{messageId}")
    ResponseEntity<List<ParameterValidationResponse>> getValidationsByMessageId(
            @Parameter(description = "ChatMessage ID", example = "10")
            @PathVariable("messageId") Long messageId
    );
}
