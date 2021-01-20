package org.icgc.argo.workflow_management.service.functions.cancel;

import static java.lang.String.format;
import static org.icgc.argo.workflow_management.service.model.Constants.NEXTFLOW_PREFIX;
import static org.icgc.argo.workflow_management.service.model.Constants.WES_PREFIX;
import static org.icgc.argo.workflow_management.service.model.KubernetesPhase.*;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.service.WebLogEventSender;
import org.icgc.argo.workflow_management.service.functions.CancelRunFunc;
import org.icgc.argo.workflow_management.service.model.KubernetesPhase;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * CancelRun function will cancel parent and child pods in kubernetes given the run/pod name.
 * Currently working with Nextflow generated pods.
 */
@Slf4j
@RequiredArgsConstructor
public class CancelRunImpl implements CancelRunFunc {
  private final NextflowProperties config;
  private final WebLogEventSender webLogSender;
  private final Scheduler scheduler;

  @Override
  public Mono<RunsResponse> apply(String runId) {
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
    val state = getPhase(runId);

    if (state.equals(FAILED)) {
      return handleFailedPod(runId);
    }

    // can only cancel when executor pod is in running or failed state
    // so throw an exception if not either of those two states
    if (!state.equals(RUNNING)) {
      throw new RuntimeException(
          format(
              "Executor pod %s is in %s state, can only cancel a running workflow.", runId, state));
    }

    try (final val client = getClient()) {
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
                        format("Cannot find executor pod with runId: %s.", runId)));
    return valueOf(executorPod.getStatus().getPhase().toUpperCase());
  }

  private String handleFailedPod(String podName) {
    log.info(
        format(
            "Executor pod %s is in a failed state, sending failed pod event to weblog ...",
            podName));
    webLogSender.sendFailedPodEvent(podName);
    log.info(format("Cancellation event for pod %s has been sent to weblog.", podName));
    return podName;
  }
}
