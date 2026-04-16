package com.plasma.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Health Check", description = "서버 상태 확인 API")
public interface HealthCheckApi {

    @Operation(summary = "서버 상태 확인", description = "서버 이름, 주소, 포트, 환경(env) 정보를 반환합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "서버 정보 반환 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "env": "local",
                              "serverAddress": "localhost",
                              "serverName": "local_server",
                              "serverPort": "8080"
                            }
                            """))
    )
    @GetMapping("/hc")
    ResponseEntity<?> healthCheck();

    @Operation(summary = "홈", description = "서버 동작 확인용 루트 엔드포인트입니다.")
    @ApiResponse(responseCode = "200", description = "PLASMA BACKEND 문자열 반환")
    @GetMapping("/")
    ResponseEntity<String> home();

    @Operation(summary = "환경 조회", description = "현재 서버의 활성 환경(profile) 이름을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "환경 이름 반환")
    @GetMapping("/env")
    ResponseEntity<?> getEnv();
}
