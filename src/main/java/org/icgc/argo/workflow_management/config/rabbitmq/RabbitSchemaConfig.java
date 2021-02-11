package org.icgc.argo.workflow_management.config.rabbitmq;

import com.pivotal.rabbitmq.ReactiveRabbit;
import com.pivotal.rabbitmq.ReactiveRabbitAutoConfiguration;
import com.pivotal.rabbitmq.schema.SchemaManager;
import java.lang.reflect.Method;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.avro.Schema;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@Slf4j
@ConditionalOnBean(ReactiveRabbitAutoConfiguration.class)
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
