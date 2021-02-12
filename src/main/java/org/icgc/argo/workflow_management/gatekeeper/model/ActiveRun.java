package org.icgc.argo.workflow_management.gatekeeper.model;

import javax.persistence.*;
import lombok.*;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.springframework.context.annotation.Profile;

@Profile("gatekeeper")
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
