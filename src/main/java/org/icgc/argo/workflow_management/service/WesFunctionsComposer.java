package org.icgc.argo.workflow_management.service;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.secret.SecretProvider;
import org.icgc.argo.workflow_management.service.functions.CancelRunFunc;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.functions.cancel.CancelRunImpl;
import org.icgc.argo.workflow_management.service.functions.cancel.CancelRunUnsupported;
import org.icgc.argo.workflow_management.service.functions.start.*;
import org.icgc.argo.workflow_management.service.model.WorkflowManagementEvent;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Component responsible for creating functional beans queudToStartConsumer, StartRunFunc and
 * CancelRunFunc for WES.
 */
@Slf4j
@Component
public class WesFunctionsComposer {
  private final Scheduler scheduler = Schedulers.newElastic("nextflow-service");

  private final NextflowProperties config;
  private final SecretProvider secretProvider;
  private final WebLogEventSender webLogSender;

  @Autowired
  public WesFunctionsComposer(
      NextflowProperties config, SecretProvider secretProvider, WebLogEventSender webLogSender) {
    this.config = config;
    this.secretProvider = secretProvider;
    this.webLogSender = webLogSender;
  }

  // Wes startRun functional bean resolution
  @Bean
  @Profile("!start-is-queued & !queued-to-start") // Default setup
  public StartRunFunc startRun() {
    return createWorkflowStartRunFunction();
  }

  @Bean
  @Profile("start-is-queued")
  public StartRunFunc queueStartRun() {
    return new QueuedStartRun(webLogSender);
  }

  @Bean
  @Profile("queued-to-start & !start-is-queued")
  public StartRunFunc initializeOnly() {
    return new StartRunUnsupported();
  }

  // Wes cancelRun functional bean resolution
  @Bean
  @Profile({"!start-is-queued & !queued-to-start", "start-is-queued"})
  public CancelRunFunc cancelRunFunc() {
    return new CancelRunImpl(config, webLogSender, scheduler);
  }

  @Bean
  @Profile("queued-to-start & !start-is-queued")
  public CancelRunFunc unsupportedCancelRunFunc() {
    // profiles indicate only want to have queued-to-start consumer functionality so disable cancel
    // operation
    return new CancelRunUnsupported();
  }

  @Bean
  @Profile("queued-to-start")
  public Consumer<WorkflowManagementEvent> queudToStartConsumer() {
    val workflowStartRunFunction = createWorkflowStartRunFunction();
    return event -> {
      if (!event.getEvent().equalsIgnoreCase(WebLogEventSender.Event.QUEUED.toString())) {
        return;
      }

      log.debug("Received queue run message: " + event);
      workflowStartRunFunction.apply(event.getRunParams()).subscribe();
    };
  }

  private WorkflowStartRun createWorkflowStartRunFunction() {
    // Only one engine with fixed version for now, so just using that
    // Eventually generate ImmutableMap of workflowType & workflowTypeVersions to appropriate engine
    // startRunFuncs
    val defaultNextflowStartFunc =
        new NextflowStartRun(config, secretProvider, webLogSender, scheduler);
    return new WorkflowStartRun(defaultNextflowStartFunc);
  }
}
