package com.plasma.be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // Swagger/OpenAPI 문서의 기본 메타데이터를 설정
    @Bean
    public OpenAPI plasmaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PLASMA BACKEND")
                        .description("PLASMA backend API documentation")
                        .version("v1"));
    }
}
