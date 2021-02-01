package org.icgc.argo.workflow_management.rabbitmq;

import static org.icgc.argo.workflow_management.util.JacksonUtils.*;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import lombok.val;
import nextflow.Const;
import nextflow.extension.Bolts;
import org.icgc.argo.workflow_management.rabbitmq.schema.EngineParams;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.service.wes.model.RunParams;
import org.icgc.argo.workflow_management.service.wes.model.WfManagementEvent;
import org.icgc.argo.workflow_management.wes.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.wes.controller.model.WorkflowEngineParams;

@UtilityClass
public class WfMgmtRunMsgConverters {
  public static WfMgmtRunMsg createWfMgmtRunMsg(String runId, RunsRequest runsRequest, RunState state) {
    val requestWep = runsRequest.getWorkflowEngineParams();

    val msgWep =
        EngineParams.newBuilder()
            .setLatest(requestWep.getLatest())
            .setDefaultContainer(requestWep.getDefaultContainer())
            .setLaunchDir(requestWep.getLaunchDir())
            .setRevision(requestWep.getRevision())
            .setProjectDir(requestWep.getProjectDir())
            .setWorkDir(requestWep.getWorkDir());

    if (requestWep.getResume() != null) {
      msgWep.setResume(requestWep.getResume().toString());
    }

    return WfMgmtRunMsg.newBuilder()
        .setRunId(runId)
        .setState(state)
        .setWorkflowUrl(runsRequest.getWorkflowUrl())
        .setWorkflowParamsJsonStr(toJsonString(runsRequest.getWorkflowParams()))
        .setWorkflowEngineParams(msgWep.build())
        .setTime(Instant.now().toEpochMilli())
        .build();
  }

  public static WfMgmtRunMsg createWfMgmtRunMsg(String runId, RunState state) {
    return WfMgmtRunMsg.newBuilder()
        .setRunId(runId)
        .setTime(Instant.now().toEpochMilli())
        .setState(state)
        .build();
  }

  public static RunParams createRunParams(WfMgmtRunMsg msg) {
    val msgWep = msg.getWorkflowEngineParams();

    val params = readValue(msg.getWorkflowParamsJsonStr(), Map.class);

    val wepBuilder =
        WorkflowEngineParams.builder()
            .defaultContainer(msgWep.getDefaultContainer())
            .revision(msgWep.getRevision())
            .launchDir(msgWep.getLaunchDir())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .latest(msgWep.getLatest());

    if (msgWep.getResume() != null) {
      wepBuilder.resume(UUID.fromString(msgWep.getResume()));
    }

    return RunParams.builder()
        .runId(msg.getRunId())
        .workflowParams(params)
        .workflowEngineParams(wepBuilder.build())
        .workflowUrl(msg.getWorkflowUrl())
        .build();
  }

  public static WfManagementEvent createWfMgmtEvent(WfMgmtRunMsg msg) {
    val msgWep = msg.getWorkflowEngineParams();

    val params = readValue(msg.getWorkflowParamsJsonStr(), Map.class);

    val wepBuilder =
        WorkflowEngineParams.builder()
            .defaultContainer(msgWep.getDefaultContainer())
            .revision(msgWep.getRevision())
            .launchDir(msgWep.getLaunchDir())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .latest(msgWep.getLatest());

    if (msgWep.getResume() != null) {
      wepBuilder.resume(UUID.fromString(msgWep.getResume()));
    }

    String time =
            Bolts.format(new Date(msg.getTime()), Const.ISO_8601_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    return WfManagementEvent.builder()
        .event(msg.getState().toString())
        .runId(msg.getRunId())
        .utcTime(time)
        .workflowEngineParams(wepBuilder.build())
        .workflowParams(params)
        .workflowType(msg.getWorkflowType())
        .workflowTypeVersion(msg.getWorkflowTypeVersion())
        .workflowUrl(msg.getWorkflowUrl())
        .build();
  }
}
