package org.icgc.argo.workflow_management.service.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/** Common event message POJO for workflow services to communicate WF-management events */
@Data
@Builder
public class WorkflowManagementEvent {
  @NonNull String runName;
  @NonNull String utcTime;
  @NonNull String event;
  RunParams runParams;
}
