package org.icgc.argo.workflow_management.service;

import static java.lang.String.format;
import static java.time.OffsetDateTime.now;
import static java.time.OffsetDateTime.parse;
import static org.icgc.argo.workflow_management.util.JsonUtils.toJsonString;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.io.IOError;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.service.model.Metadata;
import org.icgc.argo.workflow_management.service.model.Workflow;
import org.icgc.argo.workflow_management.service.model.WorkflowEvent;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class WorkflowStatusMonitor implements Runnable {
  private Set<String> podNames;
  private long sleepInterval; // in milliseconds
  private DefaultKubernetesClient kubernetesClient;
  private RestTemplate restTemplate;
  private boolean isRunning;
  private String namespace;
  private String webLogUrl;

  @Autowired
  WorkflowStatusMonitor(NextflowProperties config) {
    this.podNames = new ConcurrentSkipListSet<>();
    this.sleepInterval = config.getSleepInterval();
    this.namespace = config.getK8s().getNamespace();
    kubernetesClient =
        getClient(config.getK8s().getMasterUrl(), namespace, config.getK8s().isTrustCertificate());

    if (config.getWeblogPort() == null) {
      this.webLogUrl = config.getWeblogUrl();
    } else {
      this.webLogUrl = config.getWeblogUrl() + ":" + config.getWeblogPort();
    }
    this.restTemplate = new RestTemplate();

    this.isRunning = false;
  }

  @SuppressWarnings("InfiniteLoopStatement")
  @SneakyThrows
  public void run() {
    this.isRunning = true;
    while (true) {
      try {
        kubernetesClient.pods().inNamespace(namespace).list().getItems().stream()
            .filter(this::isMonitored)
            .forEach(this::handlePod);
      } catch (Exception e) {
        log.error(format("Workflow Status Monitor threw exception %s", e.getMessage()));
      }
      Thread.sleep(sleepInterval);
    }
  }

  public boolean isRunning() {
    return this.isRunning;
  }

  public boolean isMonitored(Pod pod) {
    val name = getPodName(pod);
    return podNames.contains(name);
  }

  public void handlePod(Pod pod) {
    val name = getPodName(pod);
    val phase = getPhase(pod);
    log.info(format("Pod '%s' is currently in phase '%s'", name, phase));

    if (phase.equalsIgnoreCase("Failed")) {
      val message = getFailureMessage(pod);
      if (post(message)) {
        log.info(format("Posted failure message '%s' for pod '%s'", message, name));
        podNames.remove(name);
      }
    } else if (phase.equalsIgnoreCase("Succeeded")) {
      log.info(format("Pod '%s' completed successfully", getPodName(pod)));
      podNames.remove(name);
    }
  }

  public String getPodName(Pod p) {
    return p.getMetadata().getName();
  }

  public String getPhase(Pod pod) {
    return pod.getStatus().getPhase();
  }

  private String getFailureMessage(Pod pod) {
    val runId = pod.getMetadata().getUid();
    val start = parse(pod.getMetadata().getCreationTimestamp());
    val workflow = new Workflow(start, now());
    val m = new Metadata(workflow);
    val event = new WorkflowEvent(runId, getPodName(pod), m);
    return toJsonString(event);
  }

  private boolean post(String body) {
    boolean status;
    val headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    val request = new HttpEntity<>(body, headers);
    String result = "";
    try {

      result = restTemplate.postForObject(webLogUrl, request, String.class);
      log.info(format("Webclient returned '%s'", result));
      status = true;
    } catch (IOError error) {
      log.error(format("Failed to post '%s' to '%s'", body, webLogUrl));
      status = false;
    }

    return status;
  }

  DefaultKubernetesClient getClient(String masterUrl, String namespace, boolean trustCertificate) {
    val config =
        new ConfigBuilder()
            .withTrustCerts(trustCertificate)
            .withMasterUrl(masterUrl)
            .withNamespace(namespace)
            .build();
    return new DefaultKubernetesClient(config);
  }

  public void addRunId(String runId) {
    log.info(format("Now monitoring %s", runId));
    podNames.add(runId);
  }
}
