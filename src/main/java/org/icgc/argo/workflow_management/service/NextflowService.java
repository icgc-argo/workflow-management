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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.Objects.nonNull;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {

  @Value("${nextflow.k8s.namespace}")
  private String k8sNamespace;

  @Value("${nextflow.k8s.volMounts}")
  private String k8sVolMounts;

  public Mono<RunsResponse> run(WESRunParams params) {
    return Mono.create(
        callback -> {
          try {
            val cmd = createCmd(createLauncher(), params);
            val driver = createDriver(cmd);

            // Run it!
            driver.run(params.getWorkflow_url(), Arrays.asList());
            val exitStatus = driver.shutdown();
            val response = new RunsResponse(exitStatus == 0 ? cmd.getRunName() : "Error!");

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

  private Launcher createLauncher() throws NextflowReflectionException {
    // Add a launcher to the mix
    val launcherParams = new HashMap<String, Object>();
    val cliOptions = new CliOptions();
    cliOptions.setBackground(true);
    launcherParams.put("options", cliOptions);

    return createWithReflection(Launcher.class, launcherParams).orElseThrow(NextflowReflectionException::new);
  }

  private CmdKubeRun createCmd(@NonNull Launcher launcher, @NonNull WESRunParams params) throws NextflowReflectionException {

    // params to build CmdKubeRun object
    val cmdParams = new HashMap<String, Object>();

    val processOptions = new HashMap<String, String>();

    // todo: should come in as workflow engine options?
    processOptions.put("container", "quay.io/pancancer/pcawg-bwa-mem:latest");
    cmdParams.put("process", processOptions);
    cmdParams.put("latest", true);

    // launcher and launcher options required by CmdKubeRun
    cmdParams.put("launcher", launcher);

    // workflow name/git and workflow params from request
    cmdParams.put("args", Arrays.asList(params.getWorkflow_url()));
    cmdParams.put("params", params.getWorkflow_params());

    // K8s options from application.yml
    cmdParams.put("namespace", k8sNamespace);
    cmdParams.put("volMounts", Collections.singletonList(k8sVolMounts));

    return createWithReflection(CmdKubeRun.class, cmdParams).orElseThrow(NextflowReflectionException::new);
  }

  private K8sDriverLauncher createDriver(@NonNull CmdKubeRun cmd) throws NextflowReflectionException {

    Method checkRunName = null;

    try {
      checkRunName = CmdKubeRun.class.getDeclaredMethod("checkRunName");
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    if (checkRunName != null) {
      checkRunName.setAccessible(true);

      try {
        checkRunName.invoke(cmd);
      } catch (IllegalAccessException | InvocationTargetException e) {
        e.printStackTrace();
      }
    } else {
      throw new NextflowReflectionException("Cannot access checkRunName!");
    }

    val k8sDriverLauncherParams = new HashMap<String, Object>();
    k8sDriverLauncherParams.put("cmd", cmd);
    k8sDriverLauncherParams.put("runName", cmd.getRunName());
    k8sDriverLauncherParams.put("background", true);

    return createWithReflection(K8sDriverLauncher.class, k8sDriverLauncherParams)
        .orElseThrow(NextflowReflectionException::new);
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

  public class NextflowReflectionException extends Exception {
    NextflowReflectionException(String exception) {
      super(exception);
    }

    NextflowReflectionException() {
      super();
    }
  }
}
