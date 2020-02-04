package org.icgc.argo.workflow_management.exception;

import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
import nextflow.exception.ProcessException;
import nextflow.exception.ProcessFailedException;
import nextflow.exception.ProcessStageException;
import nextflow.exception.ProcessSubmitException;
import nextflow.exception.ProcessTemplateException;
import nextflow.exception.ProcessUnrecoverableException;
import nextflow.exception.ScriptCompilationException;
import nextflow.exception.ScriptRuntimeException;
import nextflow.exception.StopSplitIterationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Getter
@RequiredArgsConstructor
public enum NextflowHttpStatusResolver {
  NOT_FOUND(HttpStatus.NOT_FOUND,
      MissingFileException.class ,
      MissingLibraryException.class,
      MissingModuleComponentException.class,
      MissingValueException.class
  ),
  CONFLICT(HttpStatus.CONFLICT,
      DuplicateChannelNameException.class,
      DuplicateModuleIncludeException.class,
      DuplicateProcessInvocation.class
  ),
  BAD_REQUEST(HttpStatus.BAD_REQUEST,
      IllegalConfigException.class,
      IllegalDirectiveException.class,
      IllegalFileException.class,
      IllegalInvocationException.class,
      IllegalModulePath.class
  ),
  UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY,
      AbortOperationException.class
  ),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
      AbortRunException.class,
      AbortSignalException.class,
      ConfigParseException.class,
      FailedGuardException.class,
      ProcessFailedException.class,
      ProcessStageException.class,
      ProcessSubmitException.class,
      ProcessTemplateException.class,
      ProcessUnrecoverableException.class,
      ScriptCompilationException.class,
      ScriptRuntimeException.class,
      StopSplitIterationException.class
//      RuntimeException.class,
//      Exception.class,
//      Throwable.class
  );

//  ProcessException.class,  // super class for many exceptions


  private static final String NEXTFLOW_EXCEPTION_PACKAGE_NAME = ProcessException.class.getPackageName();
  private static final Map<Class<?>, NextflowHttpStatusResolver> map = Maps.newHashMap();
  static {
    stream(NextflowHttpStatusResolver.values())
        .forEach(n -> {
          map.putAll(n.getNextflowNativeExceptionTypes().stream()
              .peek(NextflowHttpStatusResolver::checkIsNextflowException)
              .collect(toMap(x -> x, y -> n)));
        });
  }

  private static void checkIsNextflowException(Class<? extends  Throwable> nextflowException){
    checkArgument(nextflowException.getPackageName().equals(NEXTFLOW_EXCEPTION_PACKAGE_NAME),
        "The class '%s' does not belong to the nextflow exception package '%s'",
        nextflowException.getName(), NEXTFLOW_EXCEPTION_PACKAGE_NAME);
  }

  NextflowHttpStatusResolver(
      HttpStatus httpStatus,
      @NonNull Class<? extends Throwable> ... nextflowNativeExceptionTypes) {
    this.nextflowNativeExceptionTypes = List.of(nextflowNativeExceptionTypes);
    this.httpStatus= httpStatus;
  }

  @NonNull private final List<Class<? extends Throwable>> nextflowNativeExceptionTypes;
  @NonNull private final HttpStatus httpStatus;


  public static Optional<HttpStatus> resolveHttpStatus(Class<? extends Throwable> nextflowException){
//    checkArgument(map.containsKey(nextflowException),
//        "Could not resolve an HttpStatusCode for nextflow exception '%s'", nextflowException.getSimpleName());
    return Optional.ofNullable(map.get(nextflowException))
        .map(NextflowHttpStatusResolver::getHttpStatus);
  }
}
