package org.icgc.argo.workflow_management.service.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowErrorEvent {

  /** workflow run ID */
  @NonNull private String runId;

  /** workflow run name */
  @NonNull private String runName;

  /** The overall state of the workflow run, mapped to WorkflowDocument's WorkflowState */
  @JsonGetter
  String getEvent() {
    return "Failed";
  }

  @NonNull private Metadata metadata;
  @NonNull private String error;
}
