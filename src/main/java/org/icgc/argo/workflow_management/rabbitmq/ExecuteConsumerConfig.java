package org.icgc.argo.workflow_management.rabbitmq;

import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createRunParams;
import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createWfMgmtEvent;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.stream.Transaction;
import com.pivotal.rabbitmq.topology.ExchangeType;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.service.wes.NextflowWebLogEventSender;
import org.icgc.argo.workflow_management.service.wes.WorkflowExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;

@Profile("execute")
@Slf4j
@Configuration
public class ExecuteConsumerConfig {
  @Value("${execute.consumer.topology.dlxName}")
  private String dlxName;

  @Value("${execute.consumer.topology.dlqName}")
  private String dlqName;

  @Value("${execute.consumer.topology.queueName}")
  private String queueName;

  @Value("${execute.consumer.topology.topicExchangeName}")
  private String topicExchangeName;

  @Value("${execute.consumer.topology.topicRoutingKeys}")
  private String[] topicRoutingKeys;

  private final NextflowWebLogEventSender webLogEventSender;
  private final WorkflowExecutionService wes;
  private final RabbitEndpointService rabbit;

  @Autowired
  public ExecuteConsumerConfig(
      WorkflowExecutionService wes,
      RabbitEndpointService rabbit,
      NextflowWebLogEventSender webLogEventSender) {
    this.wes = wes;
    this.rabbit = rabbit;
    this.webLogEventSender = webLogEventSender;
  }

  @Bean
  public Disposable wfMgmtRunMsgForExecuteConsumer() {
    return rabbit
        .declareTopology(
            topologyBuilder ->
                topologyBuilder
                    .declareExchange(topicExchangeName)
                    .type(ExchangeType.topic)
                    .and()
                    .declareQueue(queueName)
                    .boundTo(topicExchangeName, topicRoutingKeys)
                    .withDeadLetterExchange(dlxName)
                    .and()
                    .declareExchange(dlxName)
                    .and()
                    .declareQueue(dlqName)
                    .boundTo(dlxName))
        .createTransactionalConsumerStream(queueName, WfMgmtRunMsg.class)
        .receive()
        .doOnNext(consumeAndExecuteInitializeOrCancel())
        .onErrorContinue(handleError())
        .subscribe();
  }

  public Consumer<Transaction<WfMgmtRunMsg>> consumeAndExecuteInitializeOrCancel() {
    return tx -> {
      val msg = tx.get();
      log.debug("WfMgmtRunMsg received: {}", msg);

      if (msg.getState().equals(RunState.CANCELING)) {
        log.info("Cancelling: {}", msg);
        webLogEventSender.sendWfMgmtEvent(createWfMgmtEvent(msg));

        wes.cancel(msg.getRunId())
            .subscribe(
                runsResponse -> {
                  log.info("Cancelled: {}", runsResponse);
                  tx.commit();
                });
      } else if (msg.getState().equals(RunState.INITIALIZING)) {
        log.info("Initializing: {}", msg);
        webLogEventSender.sendWfMgmtEvent(createWfMgmtEvent(msg));

        val runParams = createRunParams(msg);

        wes.run(runParams)
            .subscribe(
                runsResponse -> {
                  log.info("Initialized: {}", msg);
                  tx.commit();
                });
      } else {
        log.debug("Ignoring: {}", msg);
        tx.commit();
      }
    };
  }

  public BiConsumer<Throwable, Object> handleError() {
    return (t, tx) -> {
      t.printStackTrace();
      log.error("Error occurred with: {}", tx);
      if (tx instanceof Transaction<?> && ((Transaction<?>) tx).get() instanceof WfMgmtRunMsg) {
        val msg = (WfMgmtRunMsg) ((Transaction<?>) tx).get();
        msg.setState(RunState.SYSTEM_ERROR);
        log.info("SYSTEM_ERROR: {}", msg);
        webLogEventSender.sendWfMgmtEvent(createWfMgmtEvent(msg));
        ((Transaction<?>) tx).commit();
      } else {
        log.error("Can't get WfMgmtRunMsg, transaction is lost!");
      }
    };
  }
}
