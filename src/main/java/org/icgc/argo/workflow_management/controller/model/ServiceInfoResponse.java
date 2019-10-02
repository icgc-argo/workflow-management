package org.icgc.argo.workflow_management.controller.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import javax.validation.Valid;
import java.util.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
// TODO: Move this to the events/logging service
public class ServiceInfoResponse {
  private String authInstructionsUrl;
  private String contactInfoUrl;
  private List<WorkflowEngineParameter> defaultWorkflowEngineParameters;
  private List<String> supportedFilesystemProtocols;
  private String supportedWesVersions;
  private Map<String, Object> systemStateCounts;
  private Map<String, Object> tags;
  private Map<String, Object> workflowEngineVersions;
  private Map<String, Object> workflowTypeVersions;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  public static class WorkflowEngineParameter {
    private String defaultValue;
    private String name;
    private String value;
  }
}
