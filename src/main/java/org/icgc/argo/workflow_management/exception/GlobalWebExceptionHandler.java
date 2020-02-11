package org.icgc.argo.workflow_management.exception;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.handler.ResponseStatusExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver.resolveHttpStatus;
import static org.icgc.argo.workflow_management.util.JsonUtils.toJsonString;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.resolve;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Slf4j
//@Order(-2)
//@Component
public class GlobalWebExceptionHandler {//implements WebExceptionHandler{

//  private static Optional<HttpStatus> getResponseStatus(Class<? extends Throwable> klazz){
//    if (klazz.isAnnotationPresent(ResponseStatus.class)){
//      val value = klazz.getDeclaredAnnotation(ResponseStatus.class).value();
//      val code = klazz.getDeclaredAnnotation(ResponseStatus.class).code();
//      if (value == INTERNAL_SERVER_ERROR && value != code){
//        return Optional.of(code);
//      } else
//      return Optional.of(
//    }
//    return Optional.empty();
//  }

//  @Override
  public Mono<Void> handle(ServerWebExchange serverWebExchange, Throwable throwable) {
    val errorResponse = processThrowable(throwable);
    val errorResponseString = toJsonString(errorResponse);
    val serverHttpResponse = serverWebExchange.getResponse();
    return  processResponse(serverHttpResponse, errorResponseString, errorResponse.getStatusCode());
  }

  private static HttpStatus processResponseStatus(Throwable t, long timestamp){
    HttpStatus httpStatus = null;
    val responseResult = Optional.ofNullable(findMergedAnnotation(t.getClass(), ResponseStatus.class));
    if (responseResult.isPresent()){
      httpStatus = responseResult.get().value();
      val message = isNullOrEmpty(t.getMessage()) ? responseResult.get().reason() : t.getMessage();
      log.error("Handled exception @{}::{}[{}]::{}:: {}",
          timestamp, httpStatus.getReasonPhrase(), httpStatus.value(), t.getClass().getName(),
          message);
    } else {
      httpStatus = INTERNAL_SERVER_ERROR;
      log.error("Unhandled exception @{}::{}[{}]::{}:: {}",
          timestamp, httpStatus.getReasonPhrase(), httpStatus.value(), t.getClass().getName(), t.getMessage());
    }
    return httpStatus;
  }

  private static ErrorResponse processThrowable(Throwable t){
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
//        val responseStatusResult = getResponseStatus(t.getClass());
        httpStatus = processResponseStatus(t, timestamp);
      }
      return getErrorResponseRaw(t, httpStatus, timestamp);
    }
    log.error("{}[{}] exception @{}: exceptionType='{}' message='{}'",
        httpStatus.getReasonPhrase(), httpStatus.value(),timestamp, t.getClass().getSimpleName(),t.getMessage());
    return getErrorResponseRaw(t, httpStatus, timestamp);
  }

  private static Mono<Void> processResponse(ServerHttpResponse serverHttpResponse, String responseBody, int httpStatusCode){
    serverHttpResponse.setStatusCode(resolve(httpStatusCode));
    serverHttpResponse.getHeaders().setContentType(APPLICATION_JSON);
    val dataBuffer = serverHttpResponse.bufferFactory().wrap(responseBody.getBytes());
    return serverHttpResponse.writeWith(Flux.just(dataBuffer));
  }

  private static ErrorResponse getErrorResponseRaw(Throwable ex, HttpStatus status, long timestamp) {
    return ErrorResponse.builder()
        .msg(format("[@%s]: %s",timestamp, ex.getMessage()))
        .statusCode(status.value())
        .build();
  }

}
