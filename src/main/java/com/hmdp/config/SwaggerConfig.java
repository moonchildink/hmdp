package com.hmdp.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI swaggerOpenApi() {
//        return new OpenAPI()
//                .info(new Info().title("XXX平台YYY微服务")
//                        .description("描述平台多牛逼")
//                        .version("v1.0.0"))
//                ;
        return new OpenAPI();
    }
}
