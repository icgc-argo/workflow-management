package org.icgc.argo.workflow_management.service;

import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface WorkflowExecutionService {
  @HasQueryAndMutationAccess
  Mono<RunsResponse> run(WESRunParams params);

  @HasQueryAndMutationAccess
  Mono<RunsResponse> cancel(String runId);

  @Retention(RetentionPolicy.RUNTIME)
  @PreAuthorize("@queryAndMutationScopeChecker.apply(authentication)")
  @interface HasQueryAndMutationAccess {
  }
}
