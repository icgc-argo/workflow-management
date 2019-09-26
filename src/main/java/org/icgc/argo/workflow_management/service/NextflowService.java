package org.icgc.argo.workflow_management.service;

import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.model.dto.WESRunConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {
  public Mono<String> run(WESRunConfig params) {
    return null;
  }

  public Mono<String> cancel(String runId) {
    return null;
  }

  public Mono<String> getServiceInfo() {
    return null;
  }
}
