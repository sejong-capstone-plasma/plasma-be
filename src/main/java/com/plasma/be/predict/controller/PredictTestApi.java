package com.plasma.be.predict.controller;

import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import com.plasma.be.predict.dto.PredictTestRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Predict Test", description = "AI 예측 파이프라인 단독 테스트 API")
@RequestMapping("/api/test/predict")
public interface PredictTestApi {

    @Operation(
            summary = "예측 파이프라인 직접 호출",
            description = "파라미터를 직접 입력해 /ai/pipelines/predict 응답(예측 결과 + LLM 설명)을 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "예측 성공",
                    content = @Content(schema = @Schema(implementation = PredictPipelineResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
            @ApiResponse(responseCode = "500", description = "AI 서버 오류")
    })
    @PostMapping("/raw")
    ResponseEntity<PredictPipelineResponse> predictRaw(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PredictTestRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "originalUserInput": "압력 5mTorr, 소스파워 400W, 바이어스파워 100W로 예측해줘",
                                      "processType": "ETCH",
                                      "pressure":    { "value": 5.0,   "unit": "mTorr" },
                                      "sourcePower": { "value": 400.0, "unit": "W" },
                                      "biasPower":   { "value": 100.0, "unit": "W" }
                                    }
                                    """))
            )
            @RequestBody PredictTestRequest request
    );
}
