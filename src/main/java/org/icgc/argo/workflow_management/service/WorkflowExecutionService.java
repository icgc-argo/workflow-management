package org.icgc.argo.workflow_management.service;

import org.icgc.argo.workflow_management.controller.model.RunResponse;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import reactor.core.publisher.Mono;

public interface WorkflowExecutionService {
  Mono<RunResponse> run(WESRunParams params);

  Mono<String> cancel(String runId);
}
