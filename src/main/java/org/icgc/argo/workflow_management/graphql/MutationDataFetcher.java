package org.icgc.argo.workflow_management.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.NonNull;
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

import java.util.LinkedHashMap;
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
        Map<String, Object>  args = env.getArguments();
        String runId = String.valueOf(args.get("runId"));
        return getWorkflowService().cancel(runId);
    };

    private final MonoDataFetcher<RunsResponse> postRunResolver = env -> {
        Map<String, Object>  args = env.getArguments();

        // TODO - check error handling needed??
        LinkedHashMap requestMap = (LinkedHashMap) args.get("request");

        RunsRequest runsRequest = convertValue(requestMap, RunsRequest.class);

        val runConfig =WESRunParams.builder()
                               .workflowUrl(runsRequest.getWorkflowUrl())
                               .workflowParams(runsRequest.getWorkflowParams())
                               .workflowEngineParams(runsRequest.getWorkflowEngineParams())
                               .build();

        return getWorkflowService().run(runConfig);
    };

    public Map<String, DataFetcher> mutationResolvers() {
        return Map.of(
                "cancelRun", securityContextAppliedFetcher(cancelRunResolver),
                "postRun", securityContextAppliedFetcher(postRunResolver)
        );
    }


    private <T> DataFetcher<CompletableFuture<T>> securityContextAppliedFetcher(MonoDataFetcher<T> monoDataFetcher) {
        return environment -> {
            // add reactive security context to mono subscriberContext so subscribers (i.e. method security) can access it
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
