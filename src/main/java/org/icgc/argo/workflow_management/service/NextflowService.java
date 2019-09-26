package org.icgc.argo.workflow_management.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.cli.CmdKubeRun;
import nextflow.k8s.K8sDriverLauncher;
import org.icgc.argo.workflow_management.model.dto.RunsResponse;
import org.icgc.argo.workflow_management.model.dto.WESRunConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {
  public Mono<RunsResponse> run(WESRunConfig params) {
    return Mono.create(
        callback -> {

          // Testing things out (calling command directly?)
          val cmd = new CmdKubeRun();
          cmd.setLatest(true);
          cmd.setBackground(true);

          // this looks like what we need: https://github.com/nextflow-io/nextflow/blob/de132de32c3855db6fa7b8611f92b165dcb4c859/modules/nextflow/src/main/groovy/nextflow/k8s/K8sDriverLauncher.groovy
          // TODO: extend this class and create the constructor / builder
          val driver = new K8sDriverLauncher();
          driver.run("test", new ArrayList<>());

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
