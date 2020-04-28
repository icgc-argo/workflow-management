package org.icgc.argo.workflow_management.service.model;

/** * Kubernetes phases */
public enum KubernetesPhase {
  PENDING,
  RUNNING,
  SUCCEEDED,
  FAILED,
  UNKNOWN;
}
