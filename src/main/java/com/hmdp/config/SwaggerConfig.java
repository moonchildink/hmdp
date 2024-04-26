package com.hmdp.config;

import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI swaggerOpenApi() {
//        return new OpenAPI()
//                .info(new Info().title("黑马点评")
//                        .description("黑马电频")
//                        .version("v1.0.0"))
//                ;
        return new OpenAPI();
    }
}
