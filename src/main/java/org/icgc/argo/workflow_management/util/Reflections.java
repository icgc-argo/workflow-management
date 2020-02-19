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
      T obj = objClass.newInstance();
      return Optional.of(reflectionFactory(objClass, obj, params));
    } catch (InstantiationException | IllegalAccessException e) {
      log.error("createWithReflection error", e);
    }

    return Optional.empty();
  }

  public static <T> void invokeDeclaredMethod(T obj, String methodName, Object args)
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
          method.invoke(obj, args);
        } else {
          method.invoke(obj);
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        log.error(String.format("invoke error for method: %s", methodName), e);
      }
    } else {
      throw new ReflectionUtilsException(String.format("Cannot access method: %s", methodName));
    }
  }

  public static <T> void invokeDeclaredMethod(T obj, String methodName)
      throws ReflectionUtilsException {
    invokeDeclaredMethod(obj, methodName, null);
  }

  public static Optional<ResponseStatus> findResponseStatusAnnotation(Class<? extends Throwable> klazz){
    if (klazz.isAnnotationPresent(ResponseStatus.class)){
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
