package com.plasma.be.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

@RestController
public class HealthCheckController implements HealthCheckApi {

    @Value("${server.env}")
    private String env;

    @Value("${server.port}")
    private String serverPort;

    @Value("${server.serverAddress}")
    private String serverAddress;

    @Value("${serverName}")
    private String serverName;

    // 서버 상태 확인에 필요한 기본 정보를 반환한다.
    @Override
    public ResponseEntity<?> healthCheck() {
        Map<String, String> response = new TreeMap<>();
        response.put("serverName", serverName);
        response.put("serverAddress", serverAddress);
        response.put("serverPort", serverPort);
        response.put("env", env);
        return ResponseEntity.ok(response);
    }

    // 루트 경로 접근 시 서비스 식별 문자열을 반환한다.
    @Override
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("PLASMA BACKEND");
    }

    // 현재 서버 환경값만 간단히 조회한다.
    @Override
    public ResponseEntity<?> getEnv() {
        return ResponseEntity.ok(env);
    }
}
