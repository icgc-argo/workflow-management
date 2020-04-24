package org.icgc.argo.workflow_management.service;

import static org.icgc.argo.workflow_management.util.JsonUtils.toJsonString;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import nextflow.Const;
import nextflow.extension.Bolts;
import nextflow.trace.TraceRecord;
import nextflow.util.SimpleHttpClient;
import org.icgc.argo.workflow_management.service.model.NextflowMetadata;

@AllArgsConstructor
public class NextflowWebLogEventSender {
  private final SimpleHttpClient httpClient;
  private final URL endpoint;

  public NextflowWebLogEventSender(URL endpoint) {
    this.endpoint = endpoint;
    this.httpClient = new SimpleHttpClient();
  }

  enum Event {
    started,
    completed,
    process_submitted,
    process_started,
    process_completed,
    error
  }

  @SneakyThrows
  public void sendStartEvent(NextflowMetadata meta) {
    this.sendWorkflowEvent(Event.started, meta);
  }

  public void sendCompletedEvent(NextflowMetadata meta) {
    sendWorkflowEvent(Event.completed, meta);
  }

  public void sendProcessSubmitted(TraceRecord traceRecord) {
    sendTraceEvent(Event.process_submitted, traceRecord);
  }

  public void sendProcessStarted(TraceRecord traceRecord) {
    sendTraceEvent(Event.process_started, traceRecord);
  }

  public void sendProcessCompleted(TraceRecord traceRecord) {
    sendTraceEvent(Event.process_completed, traceRecord);
  }

  public void sendErrorEvent(TraceRecord traceRecord) {
    sendTraceEvent(Event.error, traceRecord);
  }

  public void sendTraceEvent(Event event, TraceRecord traceRecord) {};

  public HashMap<String, Object> getHash(Event event, String runName) {
    var message = new HashMap<String, Object>();
    String time =
        Bolts.format(new Date(), Const.ISO_8601_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    message.put("runName", runName);
    message.put("runId", "?");
    message.put("event", event.toString());
    message.put("utcTime", time);

    return message;
  }

  public void sendWorkflowEvent(Event event, NextflowMetadata meta) {
    this.httpClient.sendHttpMessage(this.endpoint.toString(), getWorkflowMessage(event, meta));
  }

  public String getWorkflowMessage(Event event, NextflowMetadata logMessage) {
    val runName = logMessage.getWorkflow().getRunName();
    val message = getHash(event, runName);

    message.put("metadata", logMessage);

    return toJsonString(message);
  }
}
