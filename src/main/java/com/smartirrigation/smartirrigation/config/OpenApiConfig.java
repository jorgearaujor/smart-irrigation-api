package com.smartirrigation.smartirrigation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartIrrigation API")
                        .version("v1")
                        .description("API ponte entre o front-end Angular, o Firebase e o ESP32 do SmartIrrigation."));
    }
}
