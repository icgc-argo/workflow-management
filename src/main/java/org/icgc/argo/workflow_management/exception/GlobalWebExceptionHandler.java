package org.icgc.argo.workflow_management.exception;

import lombok.extern.slf4j.Slf4j;
import nextflow.exception.AbortOperationException;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Slf4j
@ControllerAdvice
public class GlobalWebExceptionHandler {
  @ExceptionHandler(NotFound.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  public Mono<ErrorResponse> resourceNotFoundException(NotFound ex, ServerHttpRequest request) {
    log.error("Resource not found exception", ex);
    return getErrorResponse(ex, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Unauthorized.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ResponseBody
  public Mono<ErrorResponse> unauthorizedException(Unauthorized ex, ServerHttpRequest request) {
    log.error("Unauthorized exception", ex);
    return getErrorResponse(ex, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler({BadRequest.class, ServerWebInputException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public Mono<ErrorResponse> badRequestException(Exception ex, ServerHttpRequest request) {
    log.error("Bad request exception", ex);
    return getErrorResponse(ex, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(AbortOperationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ResponseBody
  public Mono<ErrorResponse> unprocessableEntityException(Exception ex, ServerHttpRequest request) {
    log.error("Unprocessable entity exception", ex);
    return getErrorResponse(ex, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  public Mono<ErrorResponse> globalExceptionHandler(Exception ex, ServerHttpRequest request) {
    log.error("Unhandled exception", ex);
    return getErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private Mono<ErrorResponse> getErrorResponse(Exception ex, HttpStatus status) {
    return Mono.just(
        ErrorResponse.builder().msg(ex.getMessage()).statusCode(status.value()).build());
  }
}
