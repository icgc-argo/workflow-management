/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.argo.workflow_management.properties;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.types.ResolvedArrayType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
        .apis(
            RequestHandlerSelectors.basePackage("org.icgc.argo.workflow_management.wes.controller"))
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
