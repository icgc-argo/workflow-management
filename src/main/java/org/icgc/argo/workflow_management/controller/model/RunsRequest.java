package org.icgc.argo.workflow_management.controller.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModel;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@ApiModel(description = "A JSON of required and optional fields to run a workflow")
public class RunsRequest {
  @NotBlank(message = "workflow_url is a required field!")
  private String workflowUrl;

  private Map<String, Object> workflowParams = new HashMap<String, Object>();
  private WorkflowEngineParams workflowEngineParams = new WorkflowEngineParams();

  private Map<String, Object> workflowType;
  private String[] workflowTypeVersion;
  private Map<String, Object> tags;

  // we will not be accepting this (at least to start)
  private String[] workflowAttachment;
}
