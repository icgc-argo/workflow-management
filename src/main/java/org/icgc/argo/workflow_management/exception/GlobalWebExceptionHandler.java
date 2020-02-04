package org.icgc.argo.workflow_management.exception;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static java.lang.String.format;
import static org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver.resolveHttpStatus;
import static org.icgc.argo.workflow_management.util.JsonUtils.toJsonString;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.resolve;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Slf4j
@Order(-2)
@Component
public class GlobalWebExceptionHandler implements WebExceptionHandler{

  @Override
  public Mono<Void> handle(ServerWebExchange serverWebExchange, Throwable throwable) {
    val errorResponse = processThrowable(throwable);
    val errorResponseString = toJsonString(errorResponse);
    val serverHttpResponse = serverWebExchange.getResponse();
    serverHttpResponse.setStatusCode(resolve(errorResponse.getStatusCode()));
    serverHttpResponse.getHeaders().setContentType(APPLICATION_JSON);
    val dataBuffer = serverHttpResponse.bufferFactory().wrap(errorResponseString.getBytes());
    return serverHttpResponse.writeWith(Flux.just(dataBuffer));
  }

  private ErrorResponse processThrowable(Throwable t){
    long timestamp = System.currentTimeMillis();
    HttpStatus httpStatus = null;
    if (t instanceof  WebClientResponseException) {
      val wcr = (WebClientResponseException) t;
      httpStatus = wcr.getStatusCode();
    } else if (t instanceof ResponseStatusException){
      val rse = (ResponseStatusException)t;
      httpStatus = rse.getStatus();
    } else {
      val result = resolveHttpStatus(t.getClass());
      if (result.isPresent()){
        httpStatus = result.get();
        log.error("{}[{}] exception @{}: NextflowExceptionType='{}' message='{}'",
            httpStatus.getReasonPhrase(), httpStatus.value(),timestamp, t.getClass().getSimpleName(),t.getMessage());
      } else {
        httpStatus = INTERNAL_SERVER_ERROR;
        log.error("Unhandled exception @{}::{}[{}]::{}:: {}",
            timestamp, httpStatus.getReasonPhrase(), httpStatus.value(), t.getClass().getName(), t.getMessage());
      }
      return getErrorResponseRaw(t, httpStatus, timestamp);
    }
    log.error("{}[{}] exception @{}: exceptionType='{}' message='{}'",
        httpStatus.getReasonPhrase(), httpStatus.value(),timestamp, t.getClass().getSimpleName(),t.getMessage());
    return getErrorResponseRaw(t, httpStatus, timestamp);
  }

  private static ErrorResponse getErrorResponseRaw(Throwable ex, HttpStatus status, long timestamp) {
    return ErrorResponse.builder()
        .msg(format("[@%s]: %s",timestamp, ex.getMessage()))
        .statusCode(status.value())
        .build();
  }

}
