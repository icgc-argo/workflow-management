package org.icgc.argo.workflow_management.controller;

import org.icgc.argo.workflow_management.controller.model.ServiceInfoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/service-info")
public class ServiceInfoController {

  @GetMapping
  private Mono<ServiceInfoResponse> getServiceInfo() {
    return Mono.just(
        ServiceInfoResponse.builder()
            .authInstructionsUrl("https://example.com/auth")
            .contactInfoUrl("https://example.com/contact")
            .supportedFilesystemProtocols(Arrays.asList("s3", "SONG"))
            .supportedWesVersions("1.0.0")
            .systemStateCounts(Map.of("counts", "should come from logs? should be a different service"))
            .workflowEngineVersions(Map.of("nextflow", "19.07.0.5106"))
            .workflowTypeVersions(Map.of("pcawg_bwa_mem", "1.0.0"))
            .build());
  }
}
