package org.icgc.argo.workflow_management.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.model.dto.RunsResponse;
import org.icgc.argo.workflow_management.model.dto.WESRunConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {
  public Mono<RunsResponse> run(WESRunConfig params) {
    return Mono.create(
        callback -> {
          val response = new RunsResponse("Test Mono Response");
          try {
            callback.success(response);
          } catch (Exception e) {
            callback.error(e);
          }
        });
  }

  public Mono<String> cancel(String runId) {
    return null;
  }

  public Mono<String> getServiceInfo() {
    return null;
  }
}
