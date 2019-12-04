package org.icgc.argo.workflow_management.controller.impl;

import javax.validation.Valid;
import lombok.val;
import org.icgc.argo.workflow_management.controller.RunApi;
import org.icgc.argo.workflow_management.controller.model.RunRequest;
import org.icgc.argo.workflow_management.controller.model.RunResponse;
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
public class RunApiController implements RunApi {

  @Autowired
  @Qualifier("nextflow")
  private WorkflowExecutionService nextflowService;

  @PostMapping
  public Mono<RunResponse> postRun(@Valid @RequestBody RunRequest runRequest) {

    val wesService = resolveWesType("nextflow");

    // create run config from request
    val runConfig =
        WESRunParams.builder()
            .workflowUrl(runRequest.getWorkflowUrl())
            .workflowParams(runRequest.getWorkflowParams())
            .workflowEngineParameters(runRequest.getWorkflowEngineParameters())
            .build();

    return wesService.run(runConfig);
  }

  // This method will eventually be responsible for which workflow service we run
  private WorkflowExecutionService resolveWesType(String workflowType) {
    return nextflowService;
  }
}
