package com.plasma.be.plasma.controller;

import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Plasma Distribution", description = "시뮬레이션 데이터 조회 API")
@RequestMapping("/api/plasma/distribution")
public interface PlasmaDistributionApi {

    @Operation(
            summary = "파라미터에 가장 가까운 분포 데이터 조회",
            description = "입력된 pressure/source_power/bias_power에 가장 가까운 시뮬레이션 결과(IED, IAD)를 반환합니다. " +
                          "각 파라미터는 저장된 그리드(pressure: 2~10, source_power: 100~500, bias_power: 0~1000)에서 가장 가까운 값으로 스냅됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PlasmaDistributionResponse.class))),
            @ApiResponse(responseCode = "404", description = "해당 조건의 데이터 없음")
    })
    @GetMapping
    ResponseEntity<PlasmaDistributionResponse> findClosest(
            @Parameter(description = "압력 (mTorr)", example = "7.0")
            @RequestParam double pressure,
            @Parameter(description = "소스 파워 (W)", example = "350.0")
            @RequestParam("source_power") double sourcePower,
            @Parameter(description = "바이어스 파워 (W)", example = "150.0")
            @RequestParam("bias_power") double biasPower
    );
}
