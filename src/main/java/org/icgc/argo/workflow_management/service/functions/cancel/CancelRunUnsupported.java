package org.icgc.argo.workflow_management.service.functions.cancel;

import org.icgc.argo.workflow_management.service.functions.CancelRunFunc;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

public class CancelRunUnsupported implements CancelRunFunc {
  @Override
  public Mono<RunsResponse> apply(String param) {
    return Mono.error(new Exception("Function currently not supported"));
  }
}
