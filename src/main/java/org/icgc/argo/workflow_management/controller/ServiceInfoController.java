package org.icgc.argo.workflow_management.controller;

import org.icgc.argo.workflow_management.controller.model.ServiceInfoResponse;
import org.icgc.argo.workflow_management.controller.properties.ServiceInfoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/service-info")
// TODO: Move this to the events/logging service
public class ServiceInfoController {

  @Autowired ServiceInfoProperties serviceInfoProperties;

  // TODO: Make this work for realzzz boiii
  private static final Map<String, Object> getSystemStateCounts() {
    return Map.of(
        "UNKNOWN", 0,
        "QUEUED", 3,
        "INITIALIZING", 6,
        "RUNNING", 10,
        "PAUSED", 0,
        "COMPLETE", 9001,
        "EXECUTOR_ERROR", 0,
        "SYSTEM_ERROR", 0,
        "CANCELED", 13,
        "CANCELING", 0);
  }

  @GetMapping
  private Mono<ServiceInfoResponse> getServiceInfo() {
    return Mono.just(
        ServiceInfoResponse.builder()
            .authInstructionsUrl(serviceInfoProperties.getAuthInstructionsUrl())
            .contactInfoUrl(serviceInfoProperties.getContactInfoUrl())
            .defaultWorkflowEngineParameters(
                serviceInfoProperties.getDefaultWorkflowEngineParameters())
            .supportedFilesystemProtocols(serviceInfoProperties.getSupportedFilesystemProtocols())
            .supportedWesVersions(serviceInfoProperties.getSupportedWesVersions())
            .systemStateCounts(getSystemStateCounts())
            .workflowEngineVersions(serviceInfoProperties.getWorkflowEngineVersions())
            .workflowTypeVersions(serviceInfoProperties.getWorkflowTypeVersions())
            .build());
  }
}
