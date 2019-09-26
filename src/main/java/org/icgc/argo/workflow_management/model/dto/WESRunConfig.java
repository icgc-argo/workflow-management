package org.icgc.argo.workflow_management.model.dto;

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
public class WESRunConfig {
  @NotNull private Map<String, Object> workflow_params;
  @NotNull private String workflow_url;
}
