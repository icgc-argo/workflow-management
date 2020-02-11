package org.icgc.argo.workflow_management;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.exception.AbortOperationException;
import nextflow.exception.AbortSignalException;
import nextflow.exception.DuplicateProcessInvocation;
import nextflow.exception.IllegalFileException;
import nextflow.exception.MissingFileException;
import org.icgc.argo.workflow_management.controller.impl.RunsApiController;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.exception.CustomExceptionHandler;
import org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver;
import org.icgc.argo.workflow_management.exception.ValidationException;
import org.icgc.argo.workflow_management.service.NextflowService;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.bind.annotation.ResponseStatus;
import sun.misc.Signal;

import java.util.Collection;
import java.util.function.Supplier;

import static java.util.Arrays.stream;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver.resolveHttpStatus;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BANDWIDTH_LIMIT_EXCEEDED;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;

@Slf4j
@Import(value = {
    NextflowService.class,
    NextflowProperties.class,
    CustomExceptionHandler.class
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

  //TODO: rtisma    -- create test for https://github.com/${owner}/${repo}/blob/${branch}/${path-to-file}

  /**
   * 1- MissingFileException - 404 NOT_FOUND
   * 2 - DuplicateProcessException - 409 CONFLICT
   * 3 - IllegalFileException - 400 BEAD_REQUEST
   * 4 - AbortOperationException - 422 Unprocessable
   * 5 - AbortSignalException - 500 INTERNAL_SERVER_ERROR
   * 6 - WebClientResponseException - whatever it contains
   * 7 - ResponseStatusException - whatever it contains
   * 8 - Exception annotated with @ResponseStatus - whatever it contains
   * 9 - Exception NOT annotated with @ResponseStatus - should be 500
   */

  @Test
  public void testNotFoundErrorHandling(){
    doRunRequestError(MissingFileException::new, NOT_FOUND)
        .expectBody()
        .jsonPath("$.status_code", NOT_FOUND);
  }

  @Test
  public void testConflictErrorHandling(){
    doRunRequestError(DuplicateProcessInvocation::new, CONFLICT)
        .expectBody()
        .jsonPath("$.status_code", CONFLICT);
  }

  @Test
  public void testBadRequestErrorHandling(){
    doRunRequestError(IllegalFileException::new, NOT_FOUND)
        .expectBody()
        .jsonPath("$.status_code", NOT_FOUND);
  }

  @Test
  public void testUnprocessableEntityErrorHandling(){
    doRunRequestError(AbortOperationException::new, UNPROCESSABLE_ENTITY)
        .expectBody()
        .jsonPath("$.status_code", UNPROCESSABLE_ENTITY);
  }

  @Test
  public void testInternalServerErrorErrorHandling(){
    doRunRequestError(() -> new AbortSignalException(new Signal("somesignal")), INTERNAL_SERVER_ERROR)
        .expectBody()
        .jsonPath("$.status_code", INTERNAL_SERVER_ERROR);
  }


  private ResponseSpec doRunRequestError(Supplier<? extends Throwable> exceptionSupplier, HttpStatus expectedStatus){
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());
    try {

      setup(exceptionSupplier);
    } catch (Throwable e){
      log.info(e.getMessage());
    }
    return postRunRequestForError(req, expectedStatus);
  }

  private void setup(Supplier<? extends Throwable> exceptionSupplier){
    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Setup the mock to throw an exception
    reset(mockNextflowService);

    given(mockNextflowService.run(Mockito.any()))
        .willAnswer(i -> {
              //              throw WebClientResponseException.create(NOT_FOUND.value(), NOT_FOUND.name(), null, null, null);
                throw exceptionSupplier.get();
            }
        );

  }

  @Test
  public void testResponseStatusAnnotation(){
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());

    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Setup the mock to throw an exception
    reset(mockNextflowService);
    given(mockNextflowService.run(Mockito.any()))
        .willAnswer(i -> {
//              throw WebClientResponseException.create(NOT_FOUND.value(), NOT_FOUND.name(), null, null, null);
          throw new ValidationException("something");
            }
        );
    postRunRequestForError(req, BAD_REQUEST)
        .expectStatus().isEqualTo(BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value());

    // Assert an INTERNAL_SERVER_ERROR is thrown for the custom unhandled exception
//    postRunRequestForError(req, NOT_FOUND)
//        .expectStatus().isEqualTo(NOT_FOUND)
//        .expectBody()
//        .jsonPath("$.status_code").isEqualTo(NOT_FOUND.value());
  }

  /**
   *  Test that an invalid RunsRequest will throw BAD_REQUEST http status errors
   */
  @Test
  public void testInvalidRunsRequest(){
    // Assert a request with all fields null returns a BAD_REQUEST
    val reqAllNull = new RunsRequest();
    postRunRequestForError(reqAllNull, BAD_REQUEST)
        .expectBody()
          .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value())
          .jsonPath("$.msg").value(containsString("workflow_url is a required field!"))
          .jsonPath("$.msg").value(containsString("workflow_params is a required field!"));

    // Assert a request with only the workflowParams field defined returns a BAD_REQUEST
    val reqWorkflowUrlUndefined = new RunsRequest();
    reqWorkflowUrlUndefined.setWorkflowParams(Maps.newHashMap());
    postRunRequestForError(reqWorkflowUrlUndefined, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg").value(containsString("workflow_url is a required field!"));

    // Assert a request with the workflowParams fields defined,
    // and an empty string for the workflowUrl return a BAD_REQUEST
    val reqWorkflowUrlEmpty= new RunsRequest();
    reqWorkflowUrlEmpty.setWorkflowParams(Maps.newHashMap());
    reqWorkflowUrlEmpty.setWorkflowUrl("");
    postRunRequestForError(reqWorkflowUrlEmpty, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code").isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg").value(containsString("workflow_url is a required field!"));
  }

  /**
   * Ensure exceptions NOT defined in the NextflowHttpStatusResolver are handled as INTERNAL_SERVER_ERROR errors
   */
  @Test
  public void testUnhandledException(){
    // Set up request
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());

    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Setup the mock to throw an exception
    setupNextflowRunAndThrowError(SomeUnhandledTestException.class);

    // Assert the custom unhandled exception cannot be resolved
    val result = resolveHttpStatus(SomeUnhandledTestException.class);
    assertTrue(result.isEmpty());

    // Assert an INTERNAL_SERVER_ERROR is thrown for the custom unhandled exception
    postRunRequestForError(req, INTERNAL_SERVER_ERROR)
        .expectStatus().isEqualTo(INTERNAL_SERVER_ERROR)
        .expectBody()
          .jsonPath("$.status_code").isEqualTo(INTERNAL_SERVER_ERROR.value())
          .jsonPath("$.msg").value(not(emptyOrNullString()));

  }

  /**
   * Ensure all the exceptions in the NextflowHttpStatusResolver are handled by the custom WebExceptionHandler
   */
  @Test
  public void testGlobalErrorHandling(){
    // Setup valid request
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());

    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Assert all exceptions with argumentless constructors return the expected http status code
    stream(NextflowHttpStatusResolver.values())
        .map(NextflowHttpStatusResolver::getNextflowNativeExceptionTypes)
        .flatMap(Collection::stream)
        .filter(ErrorHandlingTests::hasArgumentlessConstructor) // not all are necessary, just a few argumentless ones
        .forEach(expectedExceptionType -> {
          val expectedHttpStatus = resolveHttpStatus(expectedExceptionType).get();
          setupNextflowRunAndThrowError(expectedExceptionType);
          postRunRequestForError(req, expectedHttpStatus);
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

  public static class SomeUnhandledTestException extends RuntimeException{
    public SomeUnhandledTestException() {
    }
  }

  @ResponseStatus(BANDWIDTH_LIMIT_EXCEEDED)
  public static class ResponseStatusTestException extends RuntimeException{
    public ResponseStatusTestException() {
    }
  }

}
