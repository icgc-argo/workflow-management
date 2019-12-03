package org.icgc.argo.workflow_management.exception;

import com.amazonaws.services.kms.model.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

@Slf4j
@ControllerAdvice
public class GlobalWebExceptionHandler {
  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  public Mono<ErrorResponse> resourceNotFoundException(
      NotFoundException ex, ServerHttpRequest request) {
    log.error("Resource not found exception", ex);
    return getErrorResponse(ex, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  public Mono<ErrorResponse> globalExceptionHandler(Exception ex, ServerHttpRequest request) {
    log.error("Unhandled exception", ex);
    return getErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private Mono<ErrorResponse> getErrorResponse(Exception ex, HttpStatus status) {
    return Mono.just(ErrorResponse.builder().msg(ex.getMessage()).statusCode(status.value()).build());
  }
}
