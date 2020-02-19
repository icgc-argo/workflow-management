package org.icgc.argo.workflow_management.exception;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static org.icgc.argo.workflow_management.util.JsonUtils.toJsonString;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.exception.AbortOperationException;
import nextflow.exception.AbortRunException;
import nextflow.exception.AbortSignalException;
import nextflow.exception.ConfigParseException;
import nextflow.exception.DuplicateChannelNameException;
import nextflow.exception.DuplicateModuleIncludeException;
import nextflow.exception.DuplicateProcessInvocation;
import nextflow.exception.FailedGuardException;
import nextflow.exception.IllegalConfigException;
import nextflow.exception.IllegalDirectiveException;
import nextflow.exception.IllegalFileException;
import nextflow.exception.IllegalInvocationException;
import nextflow.exception.IllegalModulePath;
import nextflow.exception.MissingFileException;
import nextflow.exception.MissingLibraryException;
import nextflow.exception.MissingModuleComponentException;
import nextflow.exception.MissingValueException;
import nextflow.exception.ProcessFailedException;
import nextflow.exception.ProcessStageException;
import nextflow.exception.ProcessSubmitException;
import nextflow.exception.ProcessTemplateException;
import nextflow.exception.ProcessUnrecoverableException;
import nextflow.exception.ScriptCompilationException;
import nextflow.exception.ScriptRuntimeException;
import nextflow.exception.StopSplitIterationException;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.icgc.argo.workflow_management.util.Reflections;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@ControllerAdvice
public class CustomExceptionHandler {

  @ResponseBody
  @ResponseStatus(NOT_FOUND)
  @ExceptionHandler({
    MissingFileException.class,
    MissingLibraryException.class,
    MissingModuleComponentException.class,
    MissingValueException.class
  })
  public Mono<ErrorResponse> handleNextflowNotFound(Throwable t) {
    return getNextflowErrorResponse(t, NOT_FOUND);
  }

  @ResponseBody
  @ResponseStatus(CONFLICT)
  @ExceptionHandler({
    DuplicateChannelNameException.class,
    DuplicateModuleIncludeException.class,
    DuplicateProcessInvocation.class
  })
  public Mono<ErrorResponse> handleNextflowConflict(Throwable t) {
    return getNextflowErrorResponse(t, CONFLICT);
  }

  @ResponseBody
  @ResponseStatus(BAD_REQUEST)
  @ExceptionHandler({
    IllegalConfigException.class,
    IllegalDirectiveException.class,
    IllegalFileException.class,
    IllegalInvocationException.class,
    IllegalModulePath.class
  })
  public Mono<ErrorResponse> handleNextflowBadRequest(Throwable t) {
    return getNextflowErrorResponse(t, BAD_REQUEST);
  }

  @ResponseBody
  @ResponseStatus(UNPROCESSABLE_ENTITY)
  @ExceptionHandler({AbortOperationException.class})
  public Mono<ErrorResponse> handleNextflowUnprocessableEntity(Throwable t) {
    return getNextflowErrorResponse(t, UNPROCESSABLE_ENTITY);
  }

  @ResponseBody
  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler({
    AbortRunException.class,
    ConfigParseException.class,
    AbortSignalException.class,
    FailedGuardException.class,
    ProcessFailedException.class,
    ProcessStageException.class,
    ProcessSubmitException.class,
    ProcessTemplateException.class,
    ProcessUnrecoverableException.class,
    ScriptCompilationException.class,
    ScriptRuntimeException.class,
    StopSplitIterationException.class
  })
  public Mono<ErrorResponse> handleNextflowInternalServerError(Throwable t) {
    return getNextflowErrorResponse(t, INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(WebClientResponseException.class)
  public Mono<Void> handleWebclientResponseException(
      ServerWebExchange exchange, WebClientResponseException t) {
    return handleGenericException(exchange, t, t.getStatusCode());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public Mono<Void> handleResponseStatusException(
      ServerWebExchange exchange, ResponseStatusException t) {
    return handleGenericException(exchange, t, t.getStatus());
  }

  @ExceptionHandler(Throwable.class)
  public Mono<Void> handleAllOtherExceptions(ServerWebExchange exchange, Throwable t) {
    val timestamp = System.currentTimeMillis();
    return Reflections.findResponseStatusAnnotation(t.getClass())
        .map(x -> handleResponseStatus(exchange, t, x, timestamp))
        .orElseGet(() -> handleGenericException(exchange, t, INTERNAL_SERVER_ERROR, timestamp));
  }

  private static Mono<Void> handleGenericException(
      ServerWebExchange exchange, Throwable t, HttpStatus httpStatus) {
    return handleGenericException(exchange, t, httpStatus, System.currentTimeMillis());
  }

  private static Mono<Void> handleGenericException(
      ServerWebExchange exchange, Throwable t, HttpStatus httpStatus, long timestamp) {
    log.error(
        "{}[{}] exception @{}: exceptionType='{}' message='{}'",
        httpStatus.getReasonPhrase(),
        httpStatus.value(),
        timestamp,
        t.getClass().getSimpleName(),
        t.getMessage());
    return processGenericException(exchange, t.getMessage(), httpStatus, timestamp);
  }

  private static Mono<Void> handleResponseStatus(
      ServerWebExchange exchange, Throwable t, ResponseStatus responseStatus, long timestamp) {
    val reason = extractReason(responseStatus);
    val httpStatus = extractHttpStatus(responseStatus);
    final String message =
        isNullOrEmpty(t.getMessage()) ? (isNullOrEmpty(reason) ? null : reason) : t.getMessage();
    log.error(
        "{}[{}] exception @{}: exceptionType='{}' message='{}'",
        httpStatus.getReasonPhrase(),
        httpStatus.value(),
        timestamp,
        t.getClass().getSimpleName(),
        message);
    return processGenericException(exchange, message, httpStatus, timestamp);
  }

  private static Mono<Void> processGenericException(
      ServerWebExchange exchange, String message, HttpStatus httpStatus) {
    return processGenericException(exchange, message, httpStatus, System.currentTimeMillis());
  }

  private static Mono<Void> processGenericException(
      ServerWebExchange exchange, String message, HttpStatus httpStatus, long timestamp) {
    val serverHttpResponse = exchange.getResponse();
    val errorResponse = buildErrorResponse(message, httpStatus, timestamp);
    val errorResponseString = toJsonString(errorResponse);
    serverHttpResponse.setStatusCode(httpStatus);
    serverHttpResponse.getHeaders().setContentType(APPLICATION_JSON);
    val dataBuffer = serverHttpResponse.bufferFactory().wrap(errorResponseString.getBytes());
    return serverHttpResponse.writeWith(Flux.just(dataBuffer));
  }

  private static Mono<ErrorResponse> getNextflowErrorResponse(Throwable t, HttpStatus httpStatus) {
    val timestamp = System.currentTimeMillis();
    log.error(
        "{}[{}] exception @{}: NextflowExceptionType='{}' message='{}'",
        httpStatus.getReasonPhrase(),
        httpStatus.value(),
        timestamp,
        t.getClass().getSimpleName(),
        t.getMessage());
    return buildErrorResponseMono(t.getMessage(), httpStatus, timestamp);
  }

  private static Mono<ErrorResponse> buildErrorResponseMono(
      String message, HttpStatus status, long timestamp) {
    return Mono.just(buildErrorResponse(message, status, timestamp));
  }

  private static ErrorResponse buildErrorResponse(
      String message, HttpStatus status, long timestamp) {
    return ErrorResponse.builder()
        .msg(format("[@%s]: %s", timestamp, message))
        .statusCode(status.value())
        .build();
  }

  private static HttpStatus extractHttpStatus(ResponseStatus r) {
    return r.value();
  }

  private static String extractReason(ResponseStatus r) {
    return r.reason();
  }
}
