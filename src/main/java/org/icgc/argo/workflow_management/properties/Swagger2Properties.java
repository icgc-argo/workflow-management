package org.icgc.argo.workflow_management.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebFlux;

@Configuration
@EnableSwagger2WebFlux
public class Swagger2Properties {

  @Bean
  public Docket api(SwaggerProperties properties) {
    return new Docket(DocumentationType.SWAGGER_2)
        .select()
        .apis(RequestHandlerSelectors.basePackage("org.icgc.argo.workflow_management.controller"))
        .paths(PathSelectors.any())
        .build()
        .host(properties.host)
        .apiInfo(apiInfo());
  }

  @Component
  @ConfigurationProperties(prefix = "swagger")
  class SwaggerProperties {
    /** Specify host if workflow management is running behind proxy. */
    @Setter @Getter private String host = "";
  }

  ApiInfo apiInfo() {
    return new ApiInfo(
        "Workflow Management",
        "Workflow Management API Documentation",
        "0.0.2",
        "",
        "contact@overture.bio",
        "",
        "");
  }
}
