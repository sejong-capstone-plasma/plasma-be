package com.plasma.be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    // Swagger/OpenAPI 문서의 기본 메타데이터를 설정
    @Bean
    public OpenAPI plasmaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Plasma Backend API")
                        .description("""
                                대화형 AI 기반 반도체 플라즈마 공정 의사결정 지원 플랫폼 백엔드 API.

                                자연어 기반 공정 분석 요청을 접수하고, AI 서버를 통해 공정 파라미터(pressure, source_power, bias_power)를 추출·검증하여 반환합니다.
                                """)
                        .version("v1.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버")
                ));
    }
}
