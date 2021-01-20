package org.icgc.argo.workflow_management.service.functions.start;

import static org.icgc.argo.workflow_management.service.WebLogEventSender.Event.QUEUED;
import static org.icgc.argo.workflow_management.util.WesUtils.generateWesRunName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.service.WebLogEventSender;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

/**
 * StartRunFunction that queues workflow run commands to weblog, which can be consumed by middleware
 * service(s) for various usecases and later consumed by queuedToStartConsumer instance for
 * startRun.
 */
@Slf4j
@RequiredArgsConstructor
public class QueuedStartRun implements StartRunFunc {
  private final WebLogEventSender webLogSender;

  @Override
  public Mono<RunsResponse> apply(RunParams runParams) {
    String runName = runParams.getRunName();
    if (runName == null) {
      runName = generateWesRunName();
      runParams = runParams.toBuilder().runName(runName).build();
    }
    webLogSender.sendManagementEvent(runParams, QUEUED);
    log.debug("QUEUED run {}", runParams.getRunName());
    return Mono.just(new RunsResponse(runParams.getRunName()));
  }
}
