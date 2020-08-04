package org.icgc.argo.workflow_management.graphql;

import graphql.schema.DataFetcher;
import lombok.val;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;

@Component
public class MutationDataFetcher {

    private final WorkflowExecutionService nextflowService;

    @Autowired
    public MutationDataFetcher(@Qualifier("nextflow") WorkflowExecutionService nextflowService) {
        this.nextflowService = nextflowService;
    }

    public DataFetcher<CompletableFuture<RunsResponse>> cancelRunResolver() {
        return env -> {
            val args = env.getArguments();

            String runId = String.valueOf(args.get("runId"));
            return nextflowService.cancel(runId).toFuture();
        };
    }

    public DataFetcher<CompletableFuture<RunsResponse>> postRunResolver() {
        return env -> {
            val args = env.getArguments();

            // TODO - check error handling needed??
            LinkedHashMap requestMap = (LinkedHashMap) args.get("request");

            RunsRequest runsRequest = convertValue(requestMap, RunsRequest.class);

            val runConfig =WESRunParams.builder()
                    .workflowUrl(runsRequest.getWorkflowUrl())
                    .workflowParams(runsRequest.getWorkflowParams())
                    .workflowEngineParams(runsRequest.getWorkflowEngineParams())
                    .build();

            return nextflowService.run(runConfig).toFuture();
        };
    }
}
