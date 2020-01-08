package org.icgc.argo.workflow_management.controller.impl;

import javax.validation.Valid;
import lombok.val;
import org.icgc.argo.workflow_management.controller.RunsApi;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/runs")
public class RunsApiController implements RunsApi {

  @Autowired
  @Qualifier("nextflow")
  private WorkflowExecutionService nextflowService;

  @PostMapping
  public Mono<RunsResponse> postRun(@Valid @RequestBody RunsRequest runsRequest) {

    val wesService = resolveWesType("nextflow");

    // create run config from request
    val runConfig =
        WESRunParams.builder()
            .workflowUrl(runsRequest.getWorkflowUrl())
            .workflowParams(runsRequest.getWorkflowParams())
            .workflowEngineParameters(runsRequest.getWorkflowEngineParameters())
            .build();

    return wesService.run(runConfig);
  }

  // This method will eventually be responsible for which workflow service we run
  private WorkflowExecutionService resolveWesType(String workflowType) {
    return nextflowService;
  }
}
