package org.icgc.argo.workflow_management.properties;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.types.ResolvedArrayType;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.readers.operation.HandlerMethodResolver;
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

  @Bean
  @Primary
  public HandlerMethodResolver fluxMethodResolver(TypeResolver resolver) {
    // Inject our own HandlerMethodResolver to unpack Mono/Flux types to their bound types
    return new HandlerMethodResolver(resolver) {
      @Override
      public ResolvedType methodReturnType(HandlerMethod handlerMethod) {
        ResolvedType retType = super.methodReturnType(handlerMethod);

        while (retType.getErasedType() == Mono.class || retType.getErasedType() == Flux.class) {
          if (retType.getErasedType() == Flux.class) {
            ResolvedType type = retType.getTypeBindings().getBoundType(0);
            retType = new ResolvedArrayType(type.getErasedType(), type.getTypeBindings(), type);
          } else {
            retType = retType.getTypeBindings().getBoundType(0);
          }
        }

        return retType;
      }
    };
  }
}
