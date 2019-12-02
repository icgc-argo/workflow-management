package org.icgc.argo.workflow_management.service;

import static java.util.Objects.nonNull;
import static org.icgc.argo.workflow_management.util.ParamsFile.createParamsFile;
import static org.icgc.argo.workflow_management.util.Reflections.createWithReflection;
import static org.icgc.argo.workflow_management.util.Reflections.invokeDeclaredMethod;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.cli.CliOptions;
import nextflow.cli.CmdKubeRun;
import nextflow.cli.Launcher;
import nextflow.k8s.K8sDriverLauncher;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.exception.NextflowRunException;
import org.icgc.argo.workflow_management.exception.ReflectionUtilsException;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {

  @Autowired private NextflowProperties config;
  private Scheduler scheduler = Schedulers.newElastic("nextflow-service");

  private Scheduler scheduler = Schedulers.newElastic("nextflow-service");

  public Mono<RunsResponse> run(WESRunParams params) {
    return Mono.fromSupplier(
            () -> {
              try {
                return this.startRun(params);
              } catch (Exception e) {
                log.error("startRun error", e);
                throw new RuntimeException(e.getMessage());
              }
            })
        .map(RunsResponse::new)
        .subscribeOn(scheduler);
  }

  private String startRun(WESRunParams params) throws ReflectionUtilsException, IOException, NextflowRunException {
    val cmd = createCmd(createLauncher(), params);
    val driver = createDriver(cmd);
    driver.run(params.getWorkflowUrl(), Collections.emptyList());
    val exitStatus = driver.shutdown();

    if (exitStatus == 0) {
      return cmd.getRunName();
    } else {
      throw new NextflowRunException(
          String.format("Invalid exit status (%d) from run %s", exitStatus, cmd.getRunName()));
    }
  }

  public Mono<String> cancel(String runId) {
    return Mono.just("Unimplemented Endpoint!");
  }

  private Launcher createLauncher() throws ReflectionUtilsException {
    // Add a launcher to the mix
    val launcherParams = new HashMap<String, Object>();
    val cliOptions = new CliOptions();
    cliOptions.setBackground(true);
    launcherParams.put("options", cliOptions);

    return createWithReflection(Launcher.class, launcherParams)
        .orElseThrow(ReflectionUtilsException::new);
  }

  private CmdKubeRun createCmd(@NonNull Launcher launcher, @NonNull WESRunParams params)
      throws ReflectionUtilsException, IOException {

    // Config from application.yml
    val k8sConfig = config.getK8s();
    val webLogUrl = config.getWeblogUrl();

    // params to build CmdKubeRun object
    val cmdParams = new HashMap<String, Object>();

    // run name (used for paramsFile as well)
    val runName = UUID.randomUUID().toString();

    // assign UUID as the run name
    cmdParams.put("runName", runName);

    // always pull latest code before running
    // (does not prevent us running a specific version (revision),
    // does enforce pulling of that branch/hash before running)
    cmdParams.put("latest", true);

    // launcher and launcher options required by CmdKubeRun
    cmdParams.put("launcher", launcher);

    // workflow name/git and workflow params from request (create params file)
    cmdParams.put("args", Arrays.asList(params.getWorkflowUrl()));
    cmdParams.put("paramsFile", createParamsFile(runName, params.getWorkflowParams()));

    // K8s options from application.yml
    cmdParams.put("namespace", k8sConfig.getNamespace());
    cmdParams.put("volMounts", Collections.singletonList(k8sConfig.getVolMounts()));

    // Where to POST event-based logging
    cmdParams.put("withWebLog", webLogUrl);

    // Dynamic engine properties/config
    val workflowEngineOptions = params.getWorkflowEngineParameters();

    // Use revision if provided in workflow_engine_options
    if (nonNull(workflowEngineOptions)) {

      if (nonNull(workflowEngineOptions.getWorkflowVersion())) {
        cmdParams.put("revision", workflowEngineOptions.getWorkflowVersion());
      }

      // Process options (default docker container to run for process if not specified)
      if (nonNull(workflowEngineOptions.getDefaultContainer())) {
        val processOptions = new HashMap<String, String>();
        processOptions.put("container", workflowEngineOptions.getDefaultContainer());
        cmdParams.put("process", processOptions);
      }
    }

    return createWithReflection(CmdKubeRun.class, cmdParams)
        .orElseThrow(ReflectionUtilsException::new);
  }

  private K8sDriverLauncher createDriver(@NonNull CmdKubeRun cmd) throws ReflectionUtilsException {

    invokeDeclaredMethod(cmd, "checkRunName");

    val k8sDriverLauncherParams = new HashMap<String, Object>();
    k8sDriverLauncherParams.put("cmd", cmd);
    k8sDriverLauncherParams.put("runName", cmd.getRunName());
    k8sDriverLauncherParams.put("background", true);

    return createWithReflection(K8sDriverLauncher.class, k8sDriverLauncherParams)
        .orElseThrow(ReflectionUtilsException::new);
  }
}
