package org.icgc.argo.workflow_management.service.model;

import io.fabric8.kubernetes.api.model.Pod;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import nextflow.NextflowMeta;
import nextflow.cli.CmdKubeRun;
import nextflow.config.Manifest;
import nextflow.trace.WorkflowStats;
import nextflow.util.Duration;
import org.icgc.argo.workflow_management.service.NextFlowK8sDriverLauncher;

/** * Side effect-free data object mimic-ing nextflow's WorkflowMetadata class... */
@Data
@NoArgsConstructor
public class NextflowWorkflowMetadata {
  private String runName;
  private String scriptId;
  private Path scriptFile;
  private String scriptName;
  private String repository;
  private String commitId;
  private String revision;
  private OffsetDateTime start;
  private OffsetDateTime complete;
  private Duration duration;
  private Object container;
  private String commandLine;
  private NextflowMeta nextflow;
  private boolean success;
  private Path projectDir;
  private String projectName;
  private Path launchDir;
  private Path workDir;
  private Path homeDir;
  private String userName;
  private Integer exitStatus;
  private String errorMessage;
  private String errorReport;
  private String profile;
  private UUID sessionId;
  private boolean resume;
  private String containerEngine;
  private List<Path> configFiles;
  private WorkflowStats stats;
  private Manifest manifest;

  public void update(Pod pod) {
    this.setContainer(pod.getSpec().getContainers());
    this.setContainerEngine("Docker?");
    this.setExitStatus(0);
    this.setStart(OffsetDateTime.parse(pod.getStatus().getStartTime()));
  }

  public static NextflowWorkflowMetadata create(CmdKubeRun cmd, NextFlowK8sDriverLauncher driver) {
    NextflowWorkflowMetadata metadata = new NextflowWorkflowMetadata();
    metadata.setCommandLine(driver.getCommandLine());
    metadata.setProfile(cmd.getProfile());
    metadata.setRevision(cmd.getRevision());
    metadata.setRunName(cmd.getRunName());
    metadata.setSuccess(false);
    return metadata;
  }
}
