package org.icgc.argo.workflow_management.service;

import nextflow.k8s.K8sDriverLauncher;

public class NextFlowK8sDriverLauncher extends K8sDriverLauncher {
  public String getCommandLine() {
    return getLaunchCli();
  }
}
