package org.icgc.argo.workflow_management.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = BAD_REQUEST, reason = "a better message")
public class ValidationException extends RuntimeException {

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ValidationException(Throwable cause) {
    super(cause);
  }

  public ValidationException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
