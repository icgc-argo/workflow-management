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
import nextflow.Session;
import nextflow.util.Duration;
import org.icgc.argo.workflow_management.service.model.KubernetesPhase;

@Slf4j
@AllArgsConstructor
public class NextflowWorkflowMonitor implements Runnable {
  private DefaultKubernetesClient kubernetesClient;
  private Integer maxErrorLogLines;
  private String podName;
  private NextflowWebLogEventSender webLogSender;
  private Session session;

  public void run() {
    boolean done = false;
    while (!done) {
      try {
        PodResource<Pod, DoneablePod> pod = kubernetesClient.pods().withName(podName);
        var p = pod.get();
        var log = pod.tailingLines(maxErrorLogLines).getLog();
        done = handlePod(p, log);
      } catch (Exception e) {
        log.error(format("Workflow Status Monitor threw exception %s", e.getMessage()));
        try {
          Thread.sleep(30000);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  public boolean handlePod(Pod pod, String podLog) {
    // if the pod running nextflow has created children, we'll assume it started successfully, and
    // that it can handle it's own logging from here on in.
    if (podHasChildren()) {
      log.info(podName + " has children! Done!");
      return true;
    }

    // if the pod failed to start up, we'll log the start and end events, so that we know
    // that the pod has started, and has failed, with the pod log as the error report.
    if (podFailed(pod)) {
      log.info("Sending start event");
      webLogSender.sendStartEvent(session);
      log.info("Updating the failure message");
      var failed = updateFailure(session, podLog);
      log.info("Sending completed event" + failed.toString());
      webLogSender.sendCompletedEvent(failed);
      log.info("Event sent");
      //      try {
      //        webLogSender.onFlowComplete();
      //      } catch (Exception e) {
      //        log.error("Caught exception" + e.toString());
      //      }
      log.info(podName + " failed!");
      return true;
    }

    // otherwise, we need to check in again in a moment or two
    return false;
  }

  private boolean podHasChildren() {
    val childPods =
        kubernetesClient.pods().inNamespace(kubernetesClient.getNamespace())
            .withLabel("runName", podName).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getName().startsWith(NEXTFLOW_PREFIX))
            .collect(Collectors.toList());

    return childPods.size() > 0;
  }

  private boolean podFailed(Pod pod) {
    return getPhase(pod).equals(KubernetesPhase.failed);
  }

  public Session updateFailure(Session session, String podLog) {
    val meta = session.getWorkflowMetadata();
    meta.setComplete(now());
    meta.setDuration(Duration.between(meta.getStart(), meta.getComplete()));
    meta.setErrorReport(podLog);
    meta.setErrorMessage("Nextflow pod failed to start");
    meta.setSuccess(false);
    meta.setResume(false);
    return session;
  }

  public KubernetesPhase getPhase(Pod pod) {
    return KubernetesPhase.valueOf(pod.getStatus().getPhase().toLowerCase());
  }
}
