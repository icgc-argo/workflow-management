package org.icgc.argo.workflow_management.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workflow {
  /** When the command started executing */
  @NonNull private OffsetDateTime start;

  /** When the command stopped executing (completed, failed, or cancelled) */
  private OffsetDateTime complete;

  /** Did the workflow succeed */
  public Boolean getSuccess() {
    return false;
  }

  /** Workflow duration */
  public Integer getDuration() {
    // a workflow that takes over MAX_INT seconds (over 68 years) to complete won't happen
    return Math.toIntExact(getComplete().toEpochSecond() - getStart().toEpochSecond());
  }
}
