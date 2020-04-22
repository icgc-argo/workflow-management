package org.icgc.argo.workflow_management.service.model;

/** * Kubernetes phases */
public enum KubernetesPhase {
  pending,
  running,
  succeeded,
  failed,
  unknown
}
