package org.icgc.argo.workflow_management.service.functions.start;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.icgc.argo.workflow_management.service.WebLogEventSender.Event.INITIALIZED;
import static org.icgc.argo.workflow_management.service.model.Constants.SECRET_SUFFIX;
import static org.icgc.argo.workflow_management.util.NextflowConfigFile.createNextflowConfigFile;
import static org.icgc.argo.workflow_management.util.ParamsFile.createParamsFile;
import static org.icgc.argo.workflow_management.util.Reflections.createWithReflection;
import static org.icgc.argo.workflow_management.util.Reflections.invokeDeclaredMethod;
import static org.icgc.argo.workflow_management.util.WesUtils.generateWesRunName;
import static org.icgc.argo.workflow_management.util.WesUtils.isValidWesRunName;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.cli.CliOptions;
import nextflow.cli.CmdKubeRun;
import nextflow.cli.Launcher;
import nextflow.k8s.K8sDriverLauncher;
import nextflow.script.ScriptBinding;
import org.icgc.argo.workflow_management.exception.NextflowRunException;
import org.icgc.argo.workflow_management.exception.ReflectionUtilsException;
import org.icgc.argo.workflow_management.secret.SecretProvider;
import org.icgc.argo.workflow_management.service.NextflowWorkflowMonitor;
import org.icgc.argo.workflow_management.service.WebLogEventSender;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.NextflowMetadata;
import org.icgc.argo.workflow_management.service.model.NextflowWorkflowMetadata;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.icgc.argo.workflow_management.util.ConditionalPutMap;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Nextflow specific implementation of a StartRunFunc. Creates nextflow command, sets up weblog
 * monitor, sends weblog INITIALIZED message to weblog and starts the run in Kubernetes
 */
@Slf4j
@RequiredArgsConstructor
public class NextflowStartRun implements StartRunFunc {
  /** Dependencies */
  private final NextflowProperties config;

  private final SecretProvider secretProvider;
  private final WebLogEventSender webLogSender;

  /** State */
  private final Scheduler scheduler;

  @Override
  public Mono<RunsResponse> apply(RunParams params) {
    return Mono.fromSupplier(
            () -> {
              try {
                return this.startRun(params);
              } catch (RuntimeException e) {
                // rethrow runtime exception for GlobalExceptionHandler
                log.error("nextflow runtime exception", e);
                throw e;
              } catch (Exception e) {
                log.error("startRun exception", e);
                throw new RuntimeException(e.getMessage());
              }
            })
        .map(RunsResponse::new)
        .subscribeOn(scheduler);
  }

  private String startRun(RunParams params)
      throws ReflectionUtilsException, IOException, NextflowRunException {
    val cmd = createCmd(createLauncher(), params);

    val driver = createDriver(cmd);
    driver.run(params.getWorkflowUrl(), Collections.emptyList());
    val exitStatus = driver.shutdown();

    if (exitStatus == 0) {

      // Build required objects for monitoring THIS run.
      val workflowMetadata = NextflowWorkflowMetadata.create(cmd, driver);
      val meta =
          new NextflowMetadata(
              workflowMetadata, new ScriptBinding.ParamsMap(params.getWorkflowParams()));
      val monitor =
          new NextflowWorkflowMonitor(
              webLogSender, meta, config.getMonitor().getMaxErrorLogLines(), getClient());

      // Schedule a workflow monitor to watch over our nextflow pod and make sure
      // that we report an error to our web-log service if it fails to run.
      scheduler.schedule(monitor, config.getMonitor().getSleepInterval(), TimeUnit.MILLISECONDS);

      return cmd.getRunName();
    } else {
      throw new NextflowRunException(
          format("Invalid exit status (%d) from run %s", exitStatus, cmd.getRunName()));
    }
  }

  DefaultKubernetesClient getClient() {
    val masterUrl = config.getK8s().getMasterUrl();
    val namespace = config.getK8s().getNamespace();
    val trustCertificate = config.getK8s().isTrustCertificate();
    val config =
        new ConfigBuilder()
            .withTrustCerts(trustCertificate)
            .withMasterUrl(masterUrl)
            .withNamespace(namespace)
            .build();
    return new DefaultKubernetesClient(config);
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

  private CmdKubeRun createCmd(@NonNull Launcher launcher, @NonNull RunParams params)
      throws ReflectionUtilsException, IOException {

    // Config from application.yml
    val k8sConfig = config.getK8s();
    val webLogUrl = config.getWeblogUrl();

    // params map to build CmdKubeRun (put if val not null)
    val cmdParams = new ConditionalPutMap<String, Object>(Objects::nonNull, new HashMap<>());

    val runName = params.getRunName();
    cmdParams.put("runName", runName);

    // launcher and launcher options required by CmdKubeRun
    cmdParams.put("launcher", launcher);

    // workflow name/git and workflow params from request (create params file)
    cmdParams.put("args", List.of(params.getWorkflowUrl()));
    cmdParams.put("paramsFile", createParamsFile(runName, params.getWorkflowParams()));

    // K8s options from application.yml
    cmdParams.put("namespace", k8sConfig.getNamespace());
    cmdParams.put("volMounts", k8sConfig.getVolMounts());

    // Where to POST event-based logging
    cmdParams.put("withWebLog", webLogUrl);

    // Dynamic engine properties/config
    val workflowEngineParams = params.getWorkflowEngineParams();

    // Create SecretName and K8s Secret
    val rdpcSecretName = String.format("%s-%s", runName, SECRET_SUFFIX);
    secretProvider
        .generateSecret()
        .ifPresentOrElse(
            secret -> {
              val kubernetesSecret =
                  getClient()
                      .secrets()
                      .createNew()
                      .withType("Opaque")
                      .withNewMetadata()
                      .withNewName(rdpcSecretName)
                      .endMetadata()
                      .withData(Map.of("secret", secret))
                      .done();
              log.debug(
                  "Secret {} in namespace {} created.",
                  kubernetesSecret.getMetadata().getName(),
                  kubernetesSecret.getMetadata().getNamespace());
            },
            () ->
                log.debug(
                    "No secret was generated, SecretProvider enabled status is: {}",
                    secretProvider.isEnabled()));

    // Write config file for run using required and optional arguments
    // Use launchDir, projectDir and/or workDir if provided in workflow_engine_options
    val config =
        createNextflowConfigFile(
            runName,
            k8sConfig.getRunAsUser(),
            k8sConfig.getServiceAccount(),
            k8sConfig.getRunNamespace(),
            workflowEngineParams.getLaunchDir(),
            workflowEngineParams.getProjectDir(),
            workflowEngineParams.getWorkDir());
    cmdParams.put("runConfig", List.of(config));

    // Resume workflow by name/id
    cmdParams.put("resume", workflowEngineParams.getResume(), Object::toString);

    // Use revision if provided in workflow_engine_options
    cmdParams.put("revision", workflowEngineParams.getRevision());

    // should pull latest code before running?
    // does not prevent us running a specific version (revision),
    // does enforce pulling of that branch/hash before running)
    cmdParams.put("latest", workflowEngineParams.getLatest(), v -> parseBoolean((String) v));

    // Process options (default docker container to run for process if not specified)
    if (nonNull(workflowEngineParams.getDefaultContainer())) {
      val processOptions = new HashMap<String, String>();
      processOptions.put("container", workflowEngineParams.getDefaultContainer());
      cmdParams.put("process", processOptions);
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
