package org.icgc.argo.workflow_management.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public class JsonUtils {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @SneakyThrows
  public static String toJsonString(Object o) {
    return OBJECT_MAPPER.writeValueAsString(o);
  }
}
