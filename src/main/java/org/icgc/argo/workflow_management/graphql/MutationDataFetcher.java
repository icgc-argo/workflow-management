package org.icgc.argo.workflow_management.graphql;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.val;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;

@Component
public class MutationDataFetcher {

     private final WorkflowExecutionService nextflowService;

    @Autowired
    public MutationDataFetcher(@Qualifier("nextflow") WorkflowExecutionService nextflowService) {
        this.nextflowService = nextflowService;
    }

    private final MonoDataFetcher<RunsResponse> cancelRunResolver = env -> {
        val args = env.getArguments();
        String runId = String.valueOf(args.get("runId"));
        return getWorkflowService().cancel(runId);
    };

    private final MonoDataFetcher<RunsResponse> postRunResolver = env -> {
        val args = env.getArguments();

        val requestMap = ImmutableMap.<String, Object>builder();

        if (args.get("request") != null) requestMap.putAll((Map<String, Object>) args.get("request"));

        RunsRequest runsRequest = convertValue(requestMap.build(), RunsRequest.class);

        val runConfig =WESRunParams.builder()
                               .workflowUrl(runsRequest.getWorkflowUrl())
                               .workflowParams(runsRequest.getWorkflowParams())
                               .workflowEngineParams(runsRequest.getWorkflowEngineParams())
                               .build();

        return getWorkflowService().run(runConfig);
    };

    public Map<String, DataFetcher> mutationResolvers() {
        return Map.of(
                "cancelRun", securityContextAddedFetcher(cancelRunResolver),
                "postRun", securityContextAddedFetcher(postRunResolver)
        );
    }


    private <T> DataFetcher<CompletableFuture<T>> securityContextAddedFetcher(MonoDataFetcher<T> monoDataFetcher) {
        return environment -> {
            // add reactive security context to mono subscriberContext so subscribers (i.e. method security) have access
            if (environment.getContext() instanceof SecurityContext) {
                SecurityContext context = environment.getContext();
                return monoDataFetcher.apply(environment)
                               .subscriberContext(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
                               .toFuture();
            }
            return monoDataFetcher.apply(environment).toFuture();
        };
    }

    private WorkflowExecutionService getWorkflowService() { return nextflowService; }

    interface MonoDataFetcher<T> extends Function<DataFetchingEnvironment, Mono<T>> {}
}
