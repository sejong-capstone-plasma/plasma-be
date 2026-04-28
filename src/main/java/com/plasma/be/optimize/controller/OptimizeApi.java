package com.plasma.be.optimize.controller;

import com.plasma.be.optimize.client.dto.OptimizePipelineResponse;
import com.plasma.be.optimize.dto.OptimizeRequest;
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

@Tag(name = "Optimize", description = "AI 최적화 파이프라인 프록시 API")
@RequestMapping("/api/optimize")
public interface OptimizeApi {

    @Operation(
            summary = "최적화 파이프라인 직접 호출",
            description = "프론트 요청을 /ai/pipelines/optimize 로 전달하고 AI 응답 JSON을 그대로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최적화 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
            @ApiResponse(responseCode = "500", description = "AI 서버 오류")
    })
    @PostMapping("/raw")
    ResponseEntity<OptimizePipelineResponse> optimizeRaw(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OptimizeRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "originalUserInput": "현재 조건에서 식각률을 더 높이도록 최적화해줘",
                                      "processType": "ETCH",
                                      "processParams": {
                                        "pressure": { "value": 50.0, "unit": "mTorr" },
                                        "source_power": { "value": 800.0, "unit": "W" },
                                        "bias_power": { "value": 100.0, "unit": "W" }
                                      },
                                      "currentOutputs": {
                                        "etch_rate": { "value": 120.0, "unit": "nm/min" }
                                      }
                                    }
                                    """))
            )
            @RequestBody OptimizeRequest request
    );
}
