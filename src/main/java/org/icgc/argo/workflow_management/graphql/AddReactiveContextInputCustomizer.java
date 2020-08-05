package org.icgc.argo.workflow_management.graphql;

import graphql.ExecutionInput;
import graphql.spring.web.reactive.ExecutionInputCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Primary
@Profile("jwt")
public class AddReactiveContextInputCustomizer implements ExecutionInputCustomizer {
    @Override
    public Mono<ExecutionInput> customizeExecutionInput(ExecutionInput executionInput, ServerWebExchange serverWebExchange) {
        log.debug("Adding Reactive Security Context To Execution Input");
        Mono<SecurityContext> securityContextMono = ReactiveSecurityContextHolder.getContext();

        return securityContextMono
                       .map(securityContext ->  executionInput.transform(builder -> builder.context(securityContext)))
                       .switchIfEmpty(Mono.just(executionInput));
    }
}
