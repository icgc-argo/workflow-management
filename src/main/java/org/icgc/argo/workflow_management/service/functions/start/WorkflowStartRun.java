package org.icgc.argo.workflow_management.service.functions.start;

import lombok.RequiredArgsConstructor;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

/**
 * WorkflowStartRUn is responsible for selecting the correct engine run function and running it.
 * Currently only has one engine, Nextflow.
 */
@RequiredArgsConstructor
public class WorkflowStartRun implements StartRunFunc {
  // For future, replace with ImmutableMap that maps workflowType & workflowTypeVersions to
  // appropriate engine startRunFuncs, for now just nextflow
  private final StartRunFunc defaultStartRunFunc;

  @Override
  public Mono<RunsResponse> apply(RunParams runParams) {
    return resolveStartRunFunc(runParams.getWorkflowType(), runParams.getWorkflowTypeVersion())
        .apply(runParams);
  }

  private StartRunFunc resolveStartRunFunc(String workflowType, String workflowTypeVersion) {
    return defaultStartRunFunc;
  }
}
