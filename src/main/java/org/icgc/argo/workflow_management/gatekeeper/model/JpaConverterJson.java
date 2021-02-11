package org.icgc.argo.workflow_management.gatekeeper.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;

@Profile("gatekeeper")
@Converter
@Slf4j
public class JpaConverterJson implements AttributeConverter<ActiveRun.EngineParams, String> {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @SneakyThrows
  @Override
  public String convertToDatabaseColumn(ActiveRun.EngineParams engineParams) {
    //    try {
    return objectMapper.writeValueAsString(engineParams);
    //    } catch (Exception ex) {
    //      log.error("Unexpected Exception stringifying json: {}", engineParams);
    //      return null;
    //    }
  }

  @SneakyThrows
  @Override
  public ActiveRun.EngineParams convertToEntityAttribute(String dbData) {
    //    try {
    return objectMapper.readValue(dbData, ActiveRun.EngineParams.class);
    //    } catch (IOException ex) {
    //       log.error("Unexpected IOEx decoding json from database: {}", dbData);
    //      return null;
    //    }
  }
}
