package org.icgc.argo.workflow_management.service;

import java.io.File;
import nextflow.Session;
import nextflow.script.ScriptFile;
import nextflow.trace.TraceRecord;
import nextflow.trace.WebLogObserver;

public class NextflowWebLogEventSender extends WebLogObserver {
  enum Event {
    started,
    completed,
    process_submitted,
    process_started,
    process_completed,
    error
  }

  public NextflowWebLogEventSender(String url) {
    super(url);
  }

  @Override
  protected void asyncHttpMessage(String event, Object payload) {
    // don't send anything ... we'll do it explicitly
  }

  public void sendStartEvent(Session session) {
    var scriptfile = new ScriptFile(new File("/dev/null"));
    session.setCacheable(false);
    session.init(scriptfile);
    this.onFlowCreate(session);
    this.sendEvent(Event.started, createFlowPayloadFromSession(session));
  }

  public void sendCompletedEvent(Session session) {
    sendEvent(Event.completed, createFlowPayloadFromSession(session));
  }

  public void sendProcessSubmitted(TraceRecord traceRecord) {
    sendEvent(Event.process_submitted, traceRecord);
  }

  public void sendProcessStarted(TraceRecord traceRecord) {
    sendEvent(Event.process_started, traceRecord);
  }

  public void sendProcessCompleted(TraceRecord traceRecord) {
    sendEvent(Event.process_completed, traceRecord);
  }

  public void sendErrorEvent(TraceRecord traceRecord) {
    sendEvent(Event.error, traceRecord);
  }

  public void sendEvent(Event event, Object payload) {
    this.sendHttpMessage(event.toString(), payload);
  }
}
