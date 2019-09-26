package org.icgc.argo.workflow_management.service;

import org.icgc.argo.workflow_management.model.dto.WESRunConfig;
import reactor.core.publisher.Mono;

public interface WorkflowExecutionService {
  Mono<String> run(WESRunConfig params);

  Mono<String> cancel(String runId);

  Mono<String> getServiceInfo();
}
