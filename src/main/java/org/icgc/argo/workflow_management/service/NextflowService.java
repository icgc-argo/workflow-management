package org.icgc.argo.workflow_management.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.cli.CliOptions;
import nextflow.cli.CmdKubeRun;
import nextflow.cli.Launcher;
import nextflow.k8s.K8sDriverLauncher;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {
  public Mono<RunsResponse> run(WESRunParams params) {
    return Mono.create(
        callback -> {
          val cmd = createCmd(createLauncher(), params);
          val driver = createDriver(cmd);

          // Run it!
          driver.run(params.getWorkflow_url(), Arrays.asList());
          val exitStatus = driver.shutdown();
          val response = new RunsResponse(exitStatus == 0 ? cmd.getRunName() : "Error!");

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

  private Launcher createLauncher() {
    // Add a launcher to the mix
    val launcherParams = new HashMap<String, Object>();
    val cliOptions = new CliOptions();
    cliOptions.setBackground(true);
    launcherParams.put("options", cliOptions);

    return createWithReflection(Launcher.class, launcherParams).orElseThrow();
  }

  private CmdKubeRun createCmd(@NonNull Launcher launcher, @NonNull WESRunParams params) {
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
    cmdParams.put("launcher", launcher);

    return createWithReflection(CmdKubeRun.class, cmdParams).orElseThrow();
  }

  private K8sDriverLauncher createDriver(@NonNull CmdKubeRun cmd) {
    Method checkRunName = null;
    try {
      checkRunName = CmdKubeRun.class.getDeclaredMethod("checkRunName");
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    assert checkRunName != null;
    checkRunName.setAccessible(true);
    try {
      checkRunName.invoke(cmd);
    } catch (IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
    }

    val k8sDriverLauncherParams = new HashMap<String, Object>();
    k8sDriverLauncherParams.put("cmd", cmd);
    k8sDriverLauncherParams.put("runName", cmd.getRunName());
    k8sDriverLauncherParams.put("background", true);

    return createWithReflection(K8sDriverLauncher.class, k8sDriverLauncherParams).orElseThrow();
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
    map.forEach(
        (key, value) -> {
          val field = ReflectionUtils.findField(objClass, key);

          if (nonNull(field)) {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, obj, value);
          }
        });

    return obj;
  }
}
