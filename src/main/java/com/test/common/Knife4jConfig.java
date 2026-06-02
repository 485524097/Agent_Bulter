package com.test.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI butlerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Butler AI 个人记账助手接口文档")
                        .description("用于测试个人记账、预算管理、AI消费分析等接口")
                        .version("1.0.0")
                        .contact(new Contact().name("king")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8181")
                                .description("本地开发环境")
                ));
    }

    @Bean
    public GroupedOpenApi butlerApi() {
        return GroupedOpenApi.builder()
                .group("Butler接口")
                .packagesToScan("com.test")
                .pathsToMatch("/**")
                .addOperationCustomizer(globalHeader())
                .build();
    }

    @Bean
    public OperationCustomizer globalHeader() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .name("Authorization")
                    .in("header")
                    .required(false)
                    .description("登录Token，格式：Bearer token")
                    .schema(new StringSchema()));
            return operation;
        };
    }
}