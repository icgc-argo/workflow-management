package org.icgc.argo.workflow_management.model.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RunsRequest {
  @NotNull private String workflowUrl;
  @NotNull private Map<String, Object> workflowParams;

  private Map<String, Object> workflowType;
  private String[] workflowTypeVersion;
  private Map<String, Object> tags;
  private Map<String, Object> workflowEngineParameters;

  // we will not be accepting this (at least to start)
  private String[] workflowAttachment;
}
