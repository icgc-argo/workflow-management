package org.icgc.argo.workflow_management.service;

import static java.lang.String.format;
import static java.time.OffsetDateTime.now;
import static org.icgc.argo.workflow_management.service.NextflowService.NEXTFLOW_PREFIX;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.util.Duration;
import org.icgc.argo.workflow_management.service.model.KubernetesPhase;
import org.icgc.argo.workflow_management.service.model.NextflowMetadata;

@Slf4j
@AllArgsConstructor
public class NextflowWorkflowMonitor implements Runnable {
  private DefaultKubernetesClient kubernetesClient;
  private Integer maxErrorLogLines;
  private Integer sleepTime; // in ms
  private NextflowWebLogEventSender webLogSender;
  private NextflowMetadata metadata;

  public void run() {
    boolean done = false;
    val podName = metadata.getWorkflow().getRunName();
    while (!done) {
      try {
        PodResource<Pod, DoneablePod> pod = kubernetesClient.pods().withName(podName);
        var p = pod.get();
        var log = pod.tailingLines(maxErrorLogLines).getLog();
        done = handlePod(metadata, p, log);
      } catch (Exception e) {
        log.error(format("Workflow Status Monitor threw exception %s", e.getMessage()));
        throw new RuntimeException(e.getMessage());
      }
    }
  }

  public boolean handlePod(NextflowMetadata metadata, Pod pod, String podLog) {
    val podName = pod.getMetadata().getName();
    // if the pod running nextflow has created children, we'll assume it started successfully, and
    // that it can handle it's own logging from here on in.
    if (podHasChildren(podName)) {
      log.debug(podName + " has children! Done!");
      return true;
    }

    // if the pod failed to start up, we'll log the start and end events, so that we know
    // that the pod has started, and has failed, with the pod log as the error report.
    if (podFailed(pod)) {
      log.debug("Sending start event");
      webLogSender.sendStartEvent(metadata);
      log.debug("Updating the failure message");
      updateFailure(metadata, pod, podLog);
      log.debug("Sending completed event");
      webLogSender.sendCompletedEvent(metadata);
      log.debug("Event sent");
      log.debug(podName + " failed! Done!");
      return true;
    }

    // otherwise, we need to check in again in a moment or two
    return false;
  }

  private boolean podHasChildren(String podName) {
    val childPods =
        kubernetesClient.pods().inNamespace(kubernetesClient.getNamespace())
            .withLabel("runName", podName).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getName().startsWith(NEXTFLOW_PREFIX))
            .collect(Collectors.toList());

    return childPods.size() > 0;
  }

  private boolean podFailed(Pod pod) {
    return getPhase(pod).equals(KubernetesPhase.FAILED);
  }

  public void updateFailure(NextflowMetadata metadata, Pod pod, String podLog) {
    val workflow = metadata.getWorkflow();
    workflow.update(pod);

    workflow.setComplete(now());
    workflow.setDuration(Duration.between(workflow.getStart(), workflow.getComplete()));
    workflow.setErrorReport(podLog);
    workflow.setErrorMessage("Nextflow pod failed to start");
    workflow.setSuccess(false);
    workflow.setResume(false);
  }

  public KubernetesPhase getPhase(Pod pod) {
    return KubernetesPhase.valueOf(pod.getStatus().getPhase().toUpperCase());
  }
}
