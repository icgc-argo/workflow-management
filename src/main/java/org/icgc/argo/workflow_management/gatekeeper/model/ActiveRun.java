package org.icgc.argo.workflow_management.gatekeeper.model;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;
import lombok.*;
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
  private String state; // TODO - Make into enum

  @Convert(converter = JpaConverterJson.class)
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
