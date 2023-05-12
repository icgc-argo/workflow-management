/*
 * Copyright (c) 2021 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.argo.workflow_management.wes;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.icgc.argo.workflow_management.util.ParamsFile.createParamsFile;
import static org.icgc.argo.workflow_management.util.Reflections.createWithReflection;
import static org.icgc.argo.workflow_management.util.Reflections.invokeDeclaredMethod;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.Const;
import nextflow.cli.CliOptions;
import nextflow.cli.CmdKubeRun;
import nextflow.cli.Launcher;
import nextflow.config.Manifest;
import nextflow.k8s.K8sDriverLauncher;
import org.icgc.argo.workflow_management.exception.NextflowRunException;
import org.icgc.argo.workflow_management.exception.ReflectionUtilsException;
import org.icgc.argo.workflow_management.streams.WebLogEventSender;
import org.icgc.argo.workflow_management.util.ConditionalPutMap;
import org.icgc.argo.workflow_management.util.VolumeMounts;
import org.icgc.argo.workflow_management.wes.model.*;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;
import org.icgc.argo.workflow_management.wes.secret.SecretProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {

  // Constants
  public static final String NEXTFLOW_PREFIX = "nf-";
  public static final String WES_PREFIX = "wes-";
  public static final String SECRET_SUFFIX = "secret";

  // Dependencies
  private final NextflowProperties config;
  private final SecretProvider secretProvider;
  private final WebLogEventSender webLogSender;

  // State
  private final DefaultKubernetesClient workflowRunK8sClient;

  private final Scheduler scheduler;

  @Autowired
  public NextflowService(
      NextflowProperties config, SecretProvider secretProvider, WebLogEventSender webLogSender) {
    this.config = config;
    this.secretProvider = secretProvider;
    this.webLogSender = webLogSender;
    this.workflowRunK8sClient = createWorkflowRunK8sClient();
    log.debug("WorkflowRunK8sClient created");
    this.scheduler = Schedulers.newElastic("nextflow-service");
  }

  public Mono<RunsResponse> run(RunParams params) {
    log.debug("Initializing run: {}", params);
    return Mono.just(params)
        .map(this::startRun)
        .map(RunsResponse::new)
        .onErrorMap(toRuntimeException("startRun", params.getRunId()))
        .subscribeOn(scheduler);
  }

  public Mono<RunsResponse> cancel(@NonNull String runId) {
    log.debug("Cancelling run: {}", runId);
    return Mono.just(runId)
        .map(this::cancelRun)
        .map(RunsResponse::new)
        .onErrorMap(toRuntimeException("cancelRun", runId))
        .subscribeOn(scheduler);
  }

  private Function<Throwable, Throwable> toRuntimeException(String methodName, String runId) {
    return t -> {
      if (t instanceof RuntimeException) {
        log.error("nextflow runtime exception", t);
      }
      log.error(methodName + " exception", t);
      return new RuntimeException(
          format("%s error. runId: %s, msg: %s", methodName, runId, t.getMessage()));
    };
  }

  @SneakyThrows
  private String startRun(RunParams params) {
    log.debug("startRun");
    val cmd = createCmd(createLauncher(), params);
    log.debug("command created:cmd {}",cmd);
    val driver = createDriver(cmd);
    driver.run(params.getWorkflowUrl(), Collections.emptyList());
    val exitStatus = driver.shutdown();

    if (exitStatus == 0) {

      // Build required objects for monitoring THIS run.
      val workflowMetadata = new NextflowWorkflowMetadata(cmd, driver, params);
      log.debug("workflowMetadata: {}", workflowMetadata);

      log.debug("Nextflow app version: {}", Const.APP_VER);
      log.debug("Nextflow Manifest Nextflow Version: {}", workflowMetadata.getManifest().getNextflowVersion());
      log.debug("Nextflow  Version: {}", workflowMetadata.getManifest().getVersion());

      val meta = new NextflowMetadata(workflowMetadata, params.getWorkflowParams());
      val monitor =
          new NextflowWorkflowMonitor(
              webLogSender, meta, config.getMonitor().getMaxErrorLogLines(), workflowRunK8sClient);

      // Schedule a workflow monitor to watch over our nextflow pod and make sure
      // that we report an error to our web-log service if it fails to run.
      scheduler.schedule(monitor, config.getMonitor().getSleepInterval(), TimeUnit.MILLISECONDS);
      log.debug("workflow scheduled");
      return cmd.getRunName();
    } else {
      throw new NextflowRunException(
          format("Invalid exit status (%d) from run %s", exitStatus, cmd.getRunName()));
    }
  }

  /**
   * Creates a k8s client to introspect and interact with wes-* and nf-* pods
   *
   * @return the kube client to be used to interact with deployed workflow pods
   */
  private DefaultKubernetesClient createWorkflowRunK8sClient() {
    try {
      val masterUrl = config.getK8s().getMasterUrl();
      val runNamespace = config.getK8s().getRunNamespace();
      val trustCertificate = config.getK8s().isTrustCertificate();
      val config =
          new ConfigBuilder()
              .withTrustCerts(trustCertificate)
              .withMasterUrl(masterUrl)
              .withNamespace(runNamespace)
              .build();
      return new DefaultKubernetesClient(config);
    } catch (KubernetesClientException e) {
      log.error(e.getMessage(), e);
      throw new RuntimeException(e.getLocalizedMessage());
    }

  }

  @SneakyThrows
  private String cancelRun(@NonNull String runId) {
    log.debug("cancelling run");
    val state = getPhase(runId);

    if (state.equals(KubernetesPhase.FAILED)) {
      return handleFailedPod(runId);
    }

    // can only cancel when executor pod is in running or failed state
    // so throw an exception if not either of those two states
    if (!state.equals(KubernetesPhase.RUNNING)) {
      throw new RuntimeException(
          format(
              "Executor pod %s is in %s state, can only cancel a running workflow.", runId, state));
    }

    val childPods =
        workflowRunK8sClient.pods().withLabel("runName", runId).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getName().startsWith(NEXTFLOW_PREFIX))
            .collect(Collectors.toList());
    if (childPods.size() == 0) {
      throw new RuntimeException(
          format("Cannot cancel run: pod with runId %s does not exist.", runId));
    } else {
      childPods.forEach(
          pod -> {
            workflowRunK8sClient.pods().withName(pod.getMetadata().getName()).delete();
            log.info(
                format(
                    "Process pod %s with runId = %s has been deleted from namespace %s.",
                    pod.getMetadata().getName(), runId, workflowRunK8sClient.getNamespace()));
          });
    }

    return runId;
  }

  private KubernetesPhase getPhase(String runId) {
    val executorPod =
        workflowRunK8sClient.pods().withLabel("runName", runId).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getName().startsWith(WES_PREFIX))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeException(
                        format("Cannot find executor pod with runId: %s.", runId)));
    return KubernetesPhase.valueOf(executorPod.getStatus().getPhase().toUpperCase());
  }

  private String handleFailedPod(String podName) {
    log.info(
        format(
            "Executor pod %s is in a failed state, sending failed pod event to weblog ...",
            podName));
    webLogSender.sendWfMgmtEventAsync(podName, WesState.SYSTEM_ERROR);
    log.info(format("Cancellation event for pod %s has been sent to weblog.", podName));
    return podName;
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

    val runName = params.getRunId();
    cmdParams.put("runName", runName);

    // launcher and launcher options required by CmdKubeRun
    cmdParams.put("launcher", launcher);

    // workflow name/git and workflow params from request (create params file)
    cmdParams.put("args", List.of(params.getWorkflowUrl()));
    cmdParams.put("paramsFile", createParamsFile(runName, params.getWorkflowParams()));

    // Namespace that workflow-management is operating in
    cmdParams.put("namespace", k8sConfig.getNamespace());

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
                  workflowRunK8sClient
                      .secrets()
                      .createNew()
                      .withType("Opaque")
                      .withNewMetadata()
                      .withNewName(rdpcSecretName)
                      .endMetadata()
                      .withData(
                          Map.of("secret", Base64.getEncoder().encodeToString(secret.getBytes())))
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
        NextflowConfigFile.builder()
            .runName(runName)
            .runAsUser(k8sConfig.getRunAsUser())
            .serviceAccount(k8sConfig.getServiceAccount())
            .runNamespace(k8sConfig.getRunNamespace())
            .imagePullPolicy(k8sConfig.getImagePullPolicy())
            .pluginsDir(k8sConfig.getPluginsDir())
            .launchDir(workflowEngineParams.getLaunchDir())
            .projectDir(workflowEngineParams.getProjectDir())
            .workDir(workflowEngineParams.getWorkDir())
            .build()
            .getConfig();

    cmdParams.put("runConfig", List.of(config));

    // Resume workflow by name/id
    cmdParams.put("resume", workflowEngineParams.getResume(), Object::toString);

    // Use revision if provided in workflow_engine_options
    cmdParams.put("revision", workflowEngineParams.getRevision());

    // should pull latest code before running?
    // does not prevent us running a specific version (revision),
    // does enforce pulling of that branch/hash before running)
    cmdParams.put("latest", workflowEngineParams.getLatest());

    // Process options (default docker container to run for process if not specified)
    if (nonNull(workflowEngineParams.getDefaultContainer())) {
      val processOptions = new HashMap<String, String>();
      processOptions.put("container", workflowEngineParams.getDefaultContainer());
      cmdParams.put("process", processOptions);
    }

    // volume mount config for run
    cmdParams.put("volMounts", VolumeMounts.extract(k8sConfig, workflowEngineParams));

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
