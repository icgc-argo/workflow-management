package org.icgc.argo.workflow_management.properties;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebfluxProperties implements WebFluxConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {

    registry
        .addResourceHandler("/swagger-ui.html**")
        .addResourceLocations("classpath:/META-INF/resources/");

    registry
        .addResourceHandler("/webjars/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/");
  }
}
