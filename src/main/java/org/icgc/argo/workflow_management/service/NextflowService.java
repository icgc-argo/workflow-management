/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
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

package org.icgc.argo.workflow_management.service;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.icgc.argo.workflow_management.service.model.KubernetesPhase.RUNNING;
import static org.icgc.argo.workflow_management.service.model.KubernetesPhase.valueOf;
import static org.icgc.argo.workflow_management.util.NextflowConfigFile.createNextflowConfigFile;
import static org.icgc.argo.workflow_management.util.ParamsFile.createParamsFile;
import static org.icgc.argo.workflow_management.util.Reflections.createWithReflection;
import static org.icgc.argo.workflow_management.util.Reflections.invokeDeclaredMethod;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.cli.CliOptions;
import nextflow.cli.CmdKubeRun;
import nextflow.cli.Launcher;
import nextflow.k8s.K8sDriverLauncher;
import nextflow.script.ScriptBinding;
import org.icgc.argo.workflow_management.controller.model.RunsResponse;
import org.icgc.argo.workflow_management.exception.NextflowRunException;
import org.icgc.argo.workflow_management.exception.ReflectionUtilsException;
import org.icgc.argo.workflow_management.secret.SecretProvider;
import org.icgc.argo.workflow_management.service.model.KubernetesPhase;
import org.icgc.argo.workflow_management.service.model.NextflowMetadata;
import org.icgc.argo.workflow_management.service.model.NextflowWorkflowMetadata;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.icgc.argo.workflow_management.util.ConditionalPutMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service(value = "nextflow")
public class NextflowService implements WorkflowExecutionService {

  /** Constants */
  public static final String NEXTFLOW_PREFIX = "nf-";

  public static final String WES_PREFIX = "wes-";
  public static final String SECRET_SUFFIX = "secret";

  /** Dependencies */
  private final NextflowProperties config;

  private final SecretProvider secretProvider;

  /** State */
  private final Scheduler scheduler;

  @Autowired
  public NextflowService(NextflowProperties config, SecretProvider secretProvider) {
    this.config = config;
    this.secretProvider = secretProvider;
    this.scheduler = Schedulers.newElastic("nextflow-service");
  }

  public Mono<RunsResponse> run(WESRunParams params) {
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

  private String startRun(WESRunParams params)
      throws ReflectionUtilsException, IOException, NextflowRunException {
    val cmd = createCmd(createLauncher(), params);

    // Kubernetes Secret Creation if enabled
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
                      .withNamespace("default")
                      .withNewName(String.format("%s-%s", cmd.getRunName(), SECRET_SUFFIX))
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

    val driver = createDriver(cmd);
    driver.run(params.getWorkflowUrl(), Collections.emptyList());
    val exitStatus = driver.shutdown();

    if (exitStatus == 0) {
      // Schedule a workflow monitor to watch over our nextflow pod and make sure that we report an
      // error to our web-log service if it fails to run.

      val workflowMetadata = NextflowWorkflowMetadata.create(cmd, driver);
      val meta =
          new NextflowMetadata(
              workflowMetadata, new ScriptBinding.ParamsMap(params.getWorkflowParams()));
      val sender = new NextflowWebLogEventSender(new URL(config.getWeblogUrl()));
      val monitor =
          new NextflowWorkflowMonitor(
              getClient(),
              config.getMonitor().getMaxErrorLogLines(),
              config.getMonitor().getSleepInterval(),
              sender,
              meta);
      scheduler.schedule(monitor);
      return cmd.getRunName();
    } else {
      throw new NextflowRunException(
          format("Invalid exit status (%d) from run %s", exitStatus, cmd.getRunName()));
    }
  }

  public Mono<RunsResponse> cancel(@NonNull String runId) {
    return Mono.fromSupplier(
            () -> {
              try {
                return this.cancelRun(runId);
              } catch (RuntimeException e) {
                // rethrow runtime exception for GlobalExceptionHandler
                log.error("nextflow runtime exception", e);
                throw e;
              } catch (Exception e) {
                log.error("cancelRun exception", e);
                throw new RuntimeException(e.getMessage());
              }
            })
        .map(RunsResponse::new)
        .subscribeOn(scheduler);
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

  private String cancelRun(@NonNull String runId) {
    val namespace = config.getK8s().getNamespace();
    try (final val client = getClient()) {
      isPodRunning(runId);
      val childPods =
          client.pods().inNamespace(namespace).withLabel("runName", runId).list().getItems()
              .stream()
              .filter(pod -> pod.getMetadata().getName().startsWith(NEXTFLOW_PREFIX))
              .collect(Collectors.toList());
      if (childPods.size() == 0) {
        throw new RuntimeException(
            format("Cannot cancel run: pod with runId %s does not exist.", runId));
      } else {
        childPods.forEach(
            pod -> {
              client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).delete();
              log.info(
                  format(
                      "Process pod %s with runId = %s has been deleted from namespace %s.",
                      pod.getMetadata().getName(), runId, namespace));
            });
      }
    } catch (KubernetesClientException e) {
      log.error(e.getMessage(), e);
      throw e;
    }
    return runId;
  }

  private KubernetesPhase getPhase(String runId) {
    val client = getClient();
    val namespace = config.getK8s().getNamespace();
    val executorPod =
        client.pods().inNamespace(namespace).withLabel("runName", runId).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getName().startsWith(WES_PREFIX))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeException(
                        format("Cannot found executor pod with runId: %s.", runId)));
    return valueOf(executorPod.getStatus().getPhase().toUpperCase());
  }

  private void isPodRunning(String runId) {
    val state = getPhase(runId);
    // can only cancel when executor pod is in running state

    if (!state.equals(RUNNING)) {
      throw new RuntimeException(
          format(
              "Executor pod %s is in %s state, can only cancel a running workflow.", runId, state));
    }
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

    // params map to build CmdKubeRun (put if val not null)
    val cmdParams = new ConditionalPutMap<String, Object>(Objects::nonNull, new HashMap<>());

    // run name (used for paramsFile as well)
    // You may be asking yourself, why is he replacing the "-" in the UUID, this is a valid
    // question, well unfortunately when trying to resume a job, Nextflow searches for the
    // UUID format ANYWHERE in the resume string, resulting in the incorrect assumption
    // that we are passing an runId when in fact we are passing a runName ...
    // thanks Nextflow ... this workaround solves that problem
    //
    // UPDATE: The glory of Nextflow knows no bounds ... resuming by runName while possible
    // ends up reusing the run/session (yeah these are the same but still recorded separately) id
    // from the "last" run ... wtv run that was ... resulting in multiple resumed runs sharing the
    // same sessionId (we're going with this label) even though they have nothing to do with one
    // another. This is a bug in NF and warrants a PR but for now we recommend only resuming runs
    // with sessionId and never with runName
    val runName = format("wes-%s", UUID.randomUUID().toString().replace("-", ""));
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

    // Write config file for run using required and optional arguments
    // Use launchDir, projectDir and/or workDir if provided in workflow_engine_options
    val config =
        createNextflowConfigFile(
            runName,
            k8sConfig.getRunAsUser(),
            k8sConfig.getServiceAccount(),
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
