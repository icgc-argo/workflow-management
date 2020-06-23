package org.icgc.argo.workflow_management.util;

import static java.util.Objects.nonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.exception.ReflectionUtilsException;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ResponseStatus;

@Slf4j
public class Reflections {

  public static <T> Optional<T> createWithReflection(
      Class<T> objClass, Map<String, Object> params) {
    try {
      T obj = objClass.getConstructor().newInstance();
      return Optional.of(reflectionFactory(objClass, obj, params));
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      log.error("createWithReflection error", e);
    }

    return Optional.empty();
  }

  public static <T> void invokeDeclaredMethod(T obj, String methodName)
      throws ReflectionUtilsException {
    invokeDeclaredMethod(obj, methodName, null, Void.class);
  }

  public static <T> void invokeDeclaredMethod(T obj, String methodName, Object args)
      throws ReflectionUtilsException {
    invokeDeclaredMethod(obj, methodName, args, Void.class);
  }

  public static <T, U> U invokeDeclaredMethod(T obj, String methodName, Class<U> returnType)
      throws ReflectionUtilsException {
    return invokeDeclaredMethod(obj, methodName, null, returnType);
  }

  @SuppressWarnings("unchecked")
  public static <T, U> U invokeDeclaredMethod(
      T obj, String methodName, Object args, @SuppressWarnings("unused") Class<U> returnType)
      throws ReflectionUtilsException {
    Method method = null;

    try {
      method = obj.getClass().getDeclaredMethod(methodName);
    } catch (NoSuchMethodException e) {
      log.error(String.format("getDeclaredMethod error for method: %s", methodName), e);
    }

    if (method != null) {
      method.setAccessible(true);

      try {
        if (nonNull(args)) {
          return (U) method.invoke(obj, args);
        } else {
          return (U) method.invoke(obj);
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        log.error("invokeDeclaredMethod exception", e);
        throw new ReflectionUtilsException(String.format("Invoke error for method: %s", methodName));
      }
    } else {
      throw new ReflectionUtilsException(String.format("Cannot access method: %s", methodName));
    }
  }

  public static Optional<ResponseStatus> findResponseStatusAnnotation(
      Class<? extends Throwable> klazz) {
    if (klazz.isAnnotationPresent(ResponseStatus.class)) {
      val responseStatus = klazz.getDeclaredAnnotation(ResponseStatus.class);
      return Optional.of(responseStatus);
    }
    return Optional.empty();
  }

  private static <T> T reflectionFactory(Class<T> objClass, T obj, Map<String, Object> map) {
    map.forEach(
        (key, value) -> {
          val field = ReflectionUtils.findField(objClass, key);

          if (nonNull(field)) {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, obj, value);
          }
        });

    return obj;
  }
}
