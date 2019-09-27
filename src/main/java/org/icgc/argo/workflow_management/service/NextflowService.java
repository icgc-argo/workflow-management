package org.icgc.argo.workflow_management.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.cli.CliOptions;
import nextflow.cli.CmdKubeRun;
import nextflow.cli.Launcher;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

import java.util.*;

import static java.util.Objects.nonNull;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {
  public Mono<RunsResponse> run(WESRunParams params) {
    return Mono.create(
        callback -> {

          // Construct cmd params from workflow params and url
          val cmdParams = new HashMap<String, Object>();

          cmdParams.put("args", Arrays.asList(params.getWorkflow_url()));
          cmdParams.put("params", params.getWorkflow_params());

          // Add static options (todo: make into config?)
          val processOptions = new HashMap<String, String>();
          processOptions.put("container", "quay.io/pancancer/pcawg-bwa-mem:latest");

          cmdParams.put("process", processOptions);
          cmdParams.put("namespace", "nextflow-pcawg");
          cmdParams.put("latest", true);
          cmdParams.put("volMounts", Arrays.asList("nextflow-pv-claim:/mnt/volume/nextflow"));

          // Add a launcher to the mix
          val launcherParams = new HashMap<String, Object>();
          val cliOptions = new CliOptions();
          cliOptions.setBackground(true);
          launcherParams.put("options", cliOptions);

          val launcher = createWithReflection(Launcher.class, launcherParams);
          cmdParams.put("launcher", launcher.orElse(null));

          // Using reflection (because groovy) init a CmdKubeRun
          val cmd = createWithReflection(CmdKubeRun.class, cmdParams);

          // Run it!
          cmd.ifPresent(CmdKubeRun::run);

          val response = new RunsResponse("test output");

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

  private <T> Optional<T> createWithReflection(Class<T> objClass, Map<String, Object> params) {
    try {
      T obj = objClass.newInstance();
      return Optional.of(reflectionFactory(objClass, obj, params));
    } catch (InstantiationException | IllegalAccessException e) {
      log.error(e.getMessage());
    }

    return Optional.empty();
  }

  private <T> T reflectionFactory(Class<T> objClass, T obj, Map<String, Object> map) {
    map.forEach((key, value) -> {
      val field = ReflectionUtils.findField(objClass, key);

      if (nonNull(field)) {
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, obj, value);
      }
    });

    return obj;
  }
}
