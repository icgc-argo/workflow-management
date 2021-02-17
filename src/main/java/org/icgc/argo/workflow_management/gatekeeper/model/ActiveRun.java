package org.icgc.argo.workflow_management.gatekeeper.model;

import javax.persistence.*;
import lombok.*;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;

// TODO rename ActiveRun to Run after api is removed.
// Distinction is being made for now because there are a WesRun and this new Run type in this repo.
// This entity doesn't need to be migrated and can be easily renamed/updated when the db is clear.
@Entity(name = "activeruns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ActiveRun {

  @Id private String runId;
  private String workflowUrl;
  private String workflowType;
  private String workflowTypeVersion;
  private String workflowParamsJsonStr;

  @Enumerated(EnumType.STRING)
  private RunState state;

  @Convert(converter = EngineParamsConverter.class)
  private EngineParams workflowEngineParams;

  private Long timestamp;

  @Version private Long version;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class EngineParams {
    private String defaultContainer;
    private String revision;
    private String resume;
    private String launchDir;
    private String projectDir;
    private String workDir;
    private Boolean latest;
  }
}
