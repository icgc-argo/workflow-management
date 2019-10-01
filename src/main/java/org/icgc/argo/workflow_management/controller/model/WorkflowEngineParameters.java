package org.icgc.argo.workflow_management.controller.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class WorkflowEngineParameters {
  private ProcessOptions process;
  private String revision;

  @Data
  public static class ProcessOptions {
    private String container;
  }
}
