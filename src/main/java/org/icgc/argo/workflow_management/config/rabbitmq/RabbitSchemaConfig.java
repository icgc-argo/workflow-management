package org.icgc.argo.workflow_management.config.rabbitmq;

import com.pivotal.rabbitmq.ReactiveRabbit;
import com.pivotal.rabbitmq.schema.SchemaManager;
import java.lang.reflect.Method;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.avro.Schema;
import org.icgc.argo.workflow_management.stream.schema.WfMgmtRunMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Reactive RabbitMQ Streams (ver 0.0.8) doesn't register the schema by contentType when using just
 * transactional consumers. It registers them via class path which causes all messages already in
 * queue to be considered invalid since contentType not found. But with transactional producers,
 * when it produces the message there is a check to see if the contentType exists which adds it if
 * not. So when you have both working together you don't notice this, but with just consumers it
 * doesn't work. This config ensures the avro schema is available via contentType
 */
@Slf4j
@Profile({"gatekeeper", "api", "execute"})
@Configuration
public class RabbitSchemaConfig {
  private static final String CONTENT_TYPE = "application/vnd.WfMgmtRunMsg+avro";

  private final ReactiveRabbit reactiveRabbit;
  private final ApplicationContext context;

  @Autowired
  public RabbitSchemaConfig(ReactiveRabbit reactiveRabbit, ApplicationContext context) {
    this.reactiveRabbit = reactiveRabbit;
    this.context = context;
    ensureSchemas();
  }

  @SneakyThrows
  private void ensureSchemas() {
    val schemaManager = this.reactiveRabbit.schemaManager();

    val schema = schemaManager.fetchReadSchemaByContentType(CONTENT_TYPE);
    if (schema == null) {
      addClassPathSchemaToContentTypeStorage(CONTENT_TYPE, WfMgmtRunMsg.SCHEMA$);
    }
  }

  @SneakyThrows
  private void addClassPathSchemaToContentTypeStorage(String contentType, Schema schema) {
    val schemaManager = this.reactiveRabbit.schemaManager();

    Method registerMethod =
        SchemaManager.class.getDeclaredMethod(
            "importRegisteredSchema", String.class, Schema.class, Integer.class);
    registerMethod.setAccessible(true);

    log.info("Loading WfMgmtRunMsg AVRO Schema from classpath into registry with ContentType.");
    registerMethod.invoke(schemaManager, contentType, schema, null);

    val wfMgmtRunMsgSchemaObj = schemaManager.fetchReadSchemaByContentType(contentType);
    if (wfMgmtRunMsgSchemaObj.isError()) {
      log.error("Cannot load {}} schema by Content Type, shutting down.", schema.getFullName());
      SpringApplication.exit(context, () -> 1);
    } else {
      log.info(
          "Successfully loaded schema {} from classpath.", wfMgmtRunMsgSchemaObj.getFullName());
      log.info("\n\033[32m" + wfMgmtRunMsgSchemaObj.toString(true) + "\033[39m");
    }
  }
}
