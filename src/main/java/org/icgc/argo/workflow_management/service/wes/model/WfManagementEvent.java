package org.icgc.argo.workflow_management.service.wes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.*;
import org.icgc.argo.workflow_management.wes.controller.model.WorkflowEngineParams;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WfManagementEvent {
  @NonNull private String runId;
  @NonNull private String event;
  @NonNull private String utcTime;
  @NonNull private String workflowUrl;
  private String workflowType;
  private String workflowTypeVersion;
  private Map<String, Object> workflowParams;
  private WorkflowEngineParams workflowEngineParams;
}
