package org.icgc.argo.workflow_management.util;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.nonNull;

@Slf4j
public class Reflections {
  public static <T> Optional<T> createWithReflection(Class<T> objClass, Map<String, Object> params) {
    try {
      T obj = objClass.newInstance();
      return Optional.of(reflectionFactory(objClass, obj, params));
    } catch (InstantiationException | IllegalAccessException e) {
      log.error("createWithReflection error", e);
    }

    return Optional.empty();
  }

  public static <T> T reflectionFactory(Class<T> objClass, T obj, Map<String, Object> map) {
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
