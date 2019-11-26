package org.icgc.argo.workflow_management.service.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.icgc.argo.workflow_management.controller.model.WorkflowEngineParameters;

import java.util.Map;

@Data
@Builder
@RequiredArgsConstructor
public class WESRunParams {
  @NonNull private final Map<String, Object> workflowParams;
  @NonNull private final String workflowUrl;
  private final WorkflowEngineParameters workflowEngineParameters;
}
