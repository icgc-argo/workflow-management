package org.icgc.argo.workflow_management.exception;

public class NextflowRunException extends RuntimeException {
  public NextflowRunException(String exception) {
    super(exception);
  }

  public NextflowRunException() {
    super();
  }
}
