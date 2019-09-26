package org.icgc.argo.workflow_management.controller;

import lombok.val;
import org.icgc.argo.workflow_management.model.dto.RunsRequest;
import org.icgc.argo.workflow_management.model.dto.RunsResponse;
import org.icgc.argo.workflow_management.model.dto.WESRunConfig;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

@RestController
@RequestMapping("/runs")
public class RunsController {

  @Autowired
  @Qualifier("nextflow")
  private WorkflowExecutionService nextflowService;

  @PostMapping
  private Mono<RunsResponse> postRuns(@Valid @RequestBody RunsRequest runsRequest) {

    // create run config from request
    val runConfig =
        WESRunConfig.builder()
            .workflow_url(runsRequest.getWorkflowUrl())
            .workflow_params(runsRequest.getWorkflowParams())
            .build();

    return nextflowService.run(runConfig);
  }
}
