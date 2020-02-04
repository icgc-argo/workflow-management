package org.icgc.argo.workflow_management;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.icgc.argo.workflow_management.controller.impl.RunsApiController;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.icgc.argo.workflow_management.service.NextflowService;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Collection;

import static java.util.Arrays.stream;
import static org.hamcrest.Matchers.containsString;
import static org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver.resolveHttpStatus;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;

@Slf4j
@Import(value = {
    NextflowService.class,
    NextflowProperties.class
})
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = RunsApiController.class)
public class ErrorHandlingTests {

  @Mock
  private NextflowService mockNextflowService;

  @Autowired
  private RunsApiController controller;

  @Autowired
  private WebTestClient webClient;

  @Test
  public void testInvalidRunsRequest(){
    val reqAllNull = new RunsRequest();
    postRunRequestForError(reqAllNull, BAD_REQUEST)
        .expectBody()
          .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value())
          .jsonPath("$.msg").value(containsString("workflow_url is a required field!"))
          .jsonPath("$.msg").value(containsString("workflow_params is a required field!"));

    val reqWorkflowUrlUndefined = new RunsRequest();
    reqWorkflowUrlUndefined.setWorkflowParams(Maps.newHashMap());
    postRunRequestForError(reqWorkflowUrlUndefined, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg").value(containsString("workflow_url is a required field!"));

    val reqWorkflowUrlEmpty= new RunsRequest();
    reqWorkflowUrlEmpty.setWorkflowParams(Maps.newHashMap());
    reqWorkflowUrlEmpty.setWorkflowUrl("");
    postRunRequestForError(reqWorkflowUrlEmpty, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg").value(containsString("workflow_url is a required field!"));
  }

  @Test
  public void testGlobalErrorHandling(){
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);
    stream(NextflowHttpStatusResolver.values())
        .map(NextflowHttpStatusResolver::getNextflowNativeExceptionTypes)
        .flatMap(Collection::stream)
        .filter(ErrorHandlingTests::hasArgumentlessConstructor) // not all are necessary, just a few argumentless ones
        .forEach(expectedExceptionType -> {
          val expectedHttpStatus = resolveHttpStatus(expectedExceptionType).get();
          setupNextflowRunAndThrowError(expectedExceptionType);
          webClient.post()
              .uri("/runs")
              .contentType(APPLICATION_JSON)
              .accept(APPLICATION_JSON)
              .body(fromValue(req))
              .exchange()
              .expectStatus()
              .isEqualTo(expectedHttpStatus);
        });
  }

  private void setupNextflowRunAndThrowError(Class<? extends Throwable> exceptionClassToThrow){
    reset(mockNextflowService);
    given(mockNextflowService.run(Mockito.any()))
        .willAnswer(i -> {
              throw exceptionClassToThrow.getConstructor()
                  .newInstance();
            }
        );
  }

  private ResponseSpec postRunRequest(RunsRequest r){
    return webClient.post()
        .uri("/runs")
        .contentType(APPLICATION_JSON)
        .accept(APPLICATION_JSON)
        .body(fromValue(r))
        .exchange();
  }

  private ResponseSpec postRunRequestForError(RunsRequest r, HttpStatus errorStatus){
    assertTrue(errorStatus.isError());
    return postRunRequest(r)
        .expectStatus().isEqualTo(errorStatus)
        .expectHeader().contentType(APPLICATION_JSON);
  }


  private static boolean hasArgumentlessConstructor(Class<?> clazz ){
    try {
      return clazz.getConstructor().getParameterCount() == 0;
    } catch (NoSuchMethodException e){
      return false;
    }
  }

}
