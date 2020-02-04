package org.icgc.argo.workflow_management.exception;

import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
import nextflow.exception.ProcessException;
import nextflow.exception.ProcessFailedException;
import nextflow.exception.ProcessStageException;
import nextflow.exception.ProcessSubmitException;
import nextflow.exception.ProcessTemplateException;
import nextflow.exception.ProcessUnrecoverableException;
import nextflow.exception.ScriptCompilationException;
import nextflow.exception.ScriptRuntimeException;
import nextflow.exception.ShowOnlyExceptionMessage;
import nextflow.exception.StopSplitIterationException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
public enum NextflowExceptionDispatch {
  NOT_FOUND(404,
      MissingFileException.class ,
      MissingLibraryException.class,
      MissingModuleComponentException.class,
      MissingValueException.class
  ),
  CONFLICT(409,
      DuplicateChannelNameException.class,
      DuplicateModuleIncludeException.class,
      DuplicateProcessInvocation.class
  ),
  BAD_REQUEST(400,
      IllegalConfigException.class,
      IllegalDirectiveException.class,
      IllegalFileException.class,
      IllegalInvocationException.class,
      IllegalModulePath.class
  ),
  INTERNAL_SERVER_ERROR(500,
        AbortOperationException.class,
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
  );
//  ProcessException.class,  // super class for many exceptions


  private static Map<Class<?>, NextflowExceptionDispatch> map = Maps.newHashMap();
  static {
    stream(NextflowExceptionDispatch.values())
        .forEach(n -> {
          map.putAll(n.getNextflowNativeExceptionTypes().stream()
              .collect(toMap(Object::getClass, y -> n)));
        });
  }

  private NextflowExceptionDispatch(
      int httpStatusCode,
      @NonNull Class<?> ... nextflowNativeExceptionTypes) {
    this.nextflowNativeExceptionTypes = List.of(nextflowNativeExceptionTypes);
  }

  @Getter @NonNull private final List<Class<?>> nextflowNativeExceptionTypes;
}
