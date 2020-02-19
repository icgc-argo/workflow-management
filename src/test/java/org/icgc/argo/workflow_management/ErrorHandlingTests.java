package org.icgc.argo.workflow_management;

import static java.lang.String.format;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.icgc.argo.workflow_management.util.RandomGenerator.createRandomGenerator;
import static org.icgc.argo.workflow_management.util.Reflections.findResponseStatusAnnotation;
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

import com.google.common.collect.Maps;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.exception.AbortOperationException;
import nextflow.exception.AbortRunException;
import nextflow.exception.DuplicateProcessInvocation;
import nextflow.exception.IllegalFileException;
import nextflow.exception.MissingFileException;
import org.icgc.argo.workflow_management.controller.impl.RunsApiController;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.exception.GlobalExceptionHandler;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

// TODO: rtisma    create test for
// https://github.com/${owner}/${repo}/blob/${branch}/${path-to-file}
@Slf4j
@Import(value = {NextflowService.class, NextflowProperties.class, GlobalExceptionHandler.class})
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = RunsApiController.class)
public class ErrorHandlingTests {

  @Mock private NextflowService mockNextflowService;

  @Autowired private RunsApiController controller;

  @Autowired private WebTestClient webClient;

  /**
   * Test when a Netflow exception mapped as NOT_FOUND is thrown, the server responds with an
   * ErrorResponse with status NOT_FOUND
   */
  @Test
  public void testNotFoundErrorHandling() {
    doRunRequestError(MissingFileException::new, NOT_FOUND)
        .expectBody()
        .jsonPath("$.status_code", NOT_FOUND);
  }

  /**
   * Test when a Netflow exception mapped as CONFLICT is thrown, the server responds with an
   * ErrorResponse with status CONFLICT
   */
  @Test
  public void testConflictErrorHandling() {
    doRunRequestError(DuplicateProcessInvocation::new, CONFLICT)
        .expectBody()
        .jsonPath("$.status_code", CONFLICT);
  }

  /**
   * Test when a Netflow exception mapped as BAD_REQUEST is thrown, the server responds with an
   * ErrorResponse with status BAD_REQUEST
   */
  @Test
  public void testBadRequestErrorHandling() {
    doRunRequestError(IllegalFileException::new, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code", BAD_REQUEST);
  }

  /**
   * Test when a Netflow exception mapped as UNPROCESSABLE_ENTITY is thrown, the server responds
   * with an ErrorResponse with status UNPROCESSABLE_ENTITY
   */
  @Test
  public void testUnprocessableEntityErrorHandling() {
    doRunRequestError(AbortOperationException::new, UNPROCESSABLE_ENTITY)
        .expectBody()
        .jsonPath("$.status_code", UNPROCESSABLE_ENTITY);
  }

  /**
   * Test when a Netflow exception mapped as INTERNAL_SERVER_ERROR is thrown, the server responds
   * with an ErrorResponse with status INTERNAL_SERVER_ERROR
   */
  @Test
  public void testInternalServerErrorErrorHandling() {
    doRunRequestError(AbortRunException::new, INTERNAL_SERVER_ERROR)
        .expectBody()
        .jsonPath("$.status_code", INTERNAL_SERVER_ERROR);
  }

  /**
   * Test when an exception of type WebClientResponseException is thrown, the server responds with
   * an ErrorResponse with the associated HttpStatus
   */
  @Test
  public void testWebClientResponseErrorHandling() {
    val rg = createRandomGenerator("randomGen1");
    val randomHttpStatus = rg.randomEnum(HttpStatus.class, HttpStatus::isError);
    doRunRequestError(
            () ->
                WebClientResponseException.create(
                    randomHttpStatus.value(), randomHttpStatus.name(), null, null, null),
            randomHttpStatus)
        .expectBody()
        .jsonPath("$.status_code", randomHttpStatus);
  }

  /**
   * Test when exceptions of type ResponseStatusException are thrown, the server responds with an
   * ErrorResponse with the associated HttpStatus
   */
  @Test
  public void testResponseStatusExceptionErrorHandling() {
    val rg = createRandomGenerator("randomGen1");
    val randomHttpStatus = rg.randomEnum(HttpStatus.class, HttpStatus::isError);
    doRunRequestError(
            () -> new ResponseStatusException(randomHttpStatus, randomHttpStatus.getReasonPhrase()),
            randomHttpStatus)
        .expectBody()
        .jsonPath("$.status_code", randomHttpStatus);
  }

  /**
   * Test that when an exception annotated with @ResponseStatus(SOME_HTTP_STATUS) is thrown, a
   * responds of type ErrorResponse is returned with the specified HttpStatus
   */
  @Test
  public void testResponseStatusAnnotation() {
    runResponseStatusAnnotationTest(ValidationException.class, BAD_REQUEST);
    runResponseStatusAnnotationTest(ResponseStatusTestException.class, BANDWIDTH_LIMIT_EXCEEDED);
  }

  /** Test that an invalid RunsRequest will throw BAD_REQUEST http status errors */
  @Test
  public void testInvalidRunsRequest() {
    // Assert a request with all fields null returns a BAD_REQUEST
    val reqAllNull = new RunsRequest();
    postRunRequestForError(reqAllNull, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code")
        .isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg")
        .value(containsString("workflow_url is a required field!"))
        .jsonPath("$.msg")
        .value(containsString("workflow_params is a required field!"));

    // Assert a request with only the workflowParams field defined returns a BAD_REQUEST
    val reqWorkflowUrlUndefined = new RunsRequest();
    reqWorkflowUrlUndefined.setWorkflowParams(Maps.newHashMap());
    postRunRequestForError(reqWorkflowUrlUndefined, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code")
        .isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg")
        .value(containsString("workflow_url is a required field!"));

    // Assert a request with the workflowParams fields defined,
    // and an empty string for the workflowUrl return a BAD_REQUEST
    val reqWorkflowUrlEmpty = new RunsRequest();
    reqWorkflowUrlEmpty.setWorkflowParams(Maps.newHashMap());
    reqWorkflowUrlEmpty.setWorkflowUrl("");
    postRunRequestForError(reqWorkflowUrlEmpty, BAD_REQUEST)
        .expectBody()
        .jsonPath("$.status_code")
        .isEqualTo(BAD_REQUEST.value())
        .jsonPath("$.msg")
        .value(containsString("workflow_url is a required field!"));
  }

  /**
   * Ensure exceptions NOT defined in the NextflowHttpStatusResolver, or CustomErrorHandler are
   * handled as INTERNAL_SERVER_ERROR errors
   */
  @Test
  public void testUnhandledException() {
    // Set up request
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());

    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Setup the mock to throw an exception
    setupNextflowRunAndThrowError(SomeUnhandledTestException.class);

    // Assert an INTERNAL_SERVER_ERROR is thrown for the custom unhandled exception
    postRunRequestForError(req, INTERNAL_SERVER_ERROR)
        .expectStatus()
        .isEqualTo(INTERNAL_SERVER_ERROR)
        .expectBody()
        .jsonPath("$.status_code")
        .isEqualTo(INTERNAL_SERVER_ERROR.value())
        .jsonPath("$.msg")
        .value(not(emptyOrNullString()));
  }

  /**
   * Ensure all the exceptions in the NextflowHttpStatusResolver are handled by the custom
   * WebExceptionHandler
   */
  private void setupNextflowRunAndThrowError(Class<? extends Throwable> exceptionClassToThrow) {
    reset(mockNextflowService);
    given(mockNextflowService.run(Mockito.any()))
        .willAnswer(
            i -> {
              throw exceptionClassToThrow.getConstructor().newInstance();
            });
  }

  private ResponseSpec postRunRequest(RunsRequest r) {
    return webClient
        .post()
        .uri("/runs")
        .contentType(APPLICATION_JSON)
        .accept(APPLICATION_JSON)
        .body(fromValue(r))
        .exchange();
  }

  private ResponseSpec postRunRequestForError(RunsRequest r, HttpStatus errorStatus) {
    assertTrue(errorStatus.isError());
    return postRunRequest(r)
        .expectStatus()
        .isEqualTo(errorStatus)
        .expectHeader()
        .contentType(APPLICATION_JSON);
  }

  private ResponseSpec doRunRequestError(
      Supplier<? extends Throwable> exceptionSupplier, HttpStatus expectedStatus) {
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());
    setup(exceptionSupplier);
    return postRunRequestForError(req, expectedStatus);
  }

  private void setup(Supplier<? extends Throwable> exceptionSupplier) {
    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Setup the mock to throw an exception
    reset(mockNextflowService);

    given(mockNextflowService.run(Mockito.any()))
        .willAnswer(
            i -> {
              throw exceptionSupplier.get();
            });
  }

  private <T extends Throwable> void runResponseStatusAnnotationTest(
      Class<T> exceptionClass, HttpStatus expectedErrorStatus) {
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());

    // Check the exception class used in this test has a single string argument constructor
    val result = getStringConstructor(exceptionClass);
    assertTrue(
        result.isPresent(),
        format(
            "The class '%s' does not contain a constructor that takes a single String argument",
            exceptionClass.getSimpleName()));

    // Check the exeption class used in this test is annotated with ResponseStatus
    assertTrue(
        findResponseStatusAnnotation(exceptionClass).isPresent(),
        format(
            "The class '%s' does not contain the ResponseStatus annotation",
            exceptionClass.getSimpleName()));

    // Replace nextflowService dependency in the controller with a mock
    ReflectionTestUtils.setField(controller, "nextflowService", mockNextflowService);

    // Setup the mock to throw an exception
    reset(mockNextflowService);
    given(mockNextflowService.run(Mockito.any()))
        .willAnswer(
            i -> {
              throw result.get().newInstance("something");
            });
    postRunRequestForError(req, expectedErrorStatus)
        .expectStatus()
        .isEqualTo(expectedErrorStatus)
        .expectBody()
        .jsonPath("$.status_code")
        .isEqualTo(expectedErrorStatus.value());
  }

  private static <T extends Throwable> Optional<Constructor<T>> getStringConstructor(
      Class<T> klazz) {
    try {
      return Optional.of(klazz.getDeclaredConstructor(String.class));
    } catch (NoSuchMethodException e) {
      return Optional.empty();
    }
  }

  private static boolean hasArgumentlessConstructor(Class<?> clazz) {
    try {
      return clazz.getConstructor().getParameterCount() == 0;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  public static class SomeUnhandledTestException extends RuntimeException {
    public SomeUnhandledTestException() {}
  }

  @ResponseStatus(BANDWIDTH_LIMIT_EXCEEDED)
  public static class ResponseStatusTestException extends RuntimeException {
    public ResponseStatusTestException(String message) {
      super(message);
    }
  }
}
