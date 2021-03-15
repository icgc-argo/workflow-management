package org.icgc.argo.workflow_management.gatekeeper.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Converter
@Slf4j
public class EngineParamsConverter implements AttributeConverter<Run.EngineParams, String> {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @SneakyThrows
  @Override
  public String convertToDatabaseColumn(Run.EngineParams engineParams) {
    return objectMapper.writeValueAsString(engineParams);
  }

  @SneakyThrows
  @Override
  public Run.EngineParams convertToEntityAttribute(String dbData) {
    return objectMapper.readValue(dbData, Run.EngineParams.class);
  }
}
