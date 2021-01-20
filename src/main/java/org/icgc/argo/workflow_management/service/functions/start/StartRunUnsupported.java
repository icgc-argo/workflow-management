package org.icgc.argo.workflow_management.service.functions.start;

import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

public class StartRunUnsupported implements StartRunFunc {
  @Override
  public Mono<RunsResponse> apply(RunParams param) {
    return Mono.error(new Exception("Function currently not supported"));
  }
}
