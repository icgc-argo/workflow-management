package org.icgc.argo.workflow_management.service.model;

import lombok.Data;
import nextflow.script.ScriptBinding.ParamsMap;

@Data
public class NextflowMetadata {
  private final NextflowWorkflowMetadata workflow;
  private final ParamsMap params;
}
