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

@Tag(name = "Predict", description = "AI 예측 파이프라인 프록시 API")
@RequestMapping("/api/predict")
public interface PredictTestApi {

    @Operation(
            summary = "예측 파이프라인 호출",
            description = "프론트 요청을 /ai/pipelines/predict 로 전달하고 예측 결과를 반환합니다."
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
