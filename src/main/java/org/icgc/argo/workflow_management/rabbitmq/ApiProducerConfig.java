package org.icgc.argo.workflow_management.rabbitmq;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.source.OnDemandSource;
import com.pivotal.rabbitmq.source.Sender;
import com.pivotal.rabbitmq.source.Source;
import com.pivotal.rabbitmq.topology.ExchangeType;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;

@Slf4j
@Profile("api")
@Configuration
public class ApiProducerConfig {
  @Value("${api.producer.topology.dlxName}")
  private String dlxName;

  @Value("${api.producer.topology.dlqName}")
  private String dlqName;

  @Value("${api.producer.topology.queueName}")
  private String queueName;

  @Value("${api.producer.topology.topicExchangeName}")
  private String topicExchangeName;

  @Value("${api.producer.topology.topicRoutingKeys}")
  private String[] topicRoutingKeys;

  private final RabbitEndpointService rabbit;

  @Autowired
  public ApiProducerConfig(RabbitEndpointService rabbit) {
    this.rabbit = rabbit;
  }

  @Bean
  public Disposable produceWfMgmtRunMsg(Source<WfMgmtRunMsg> apiSourceMsgs) {
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
        .createTransactionalProducerStream(WfMgmtRunMsg.class)
        .route()
        .toExchange(topicExchangeName)
        .withRoutingKey(routingKeySelector())
        .then()
        .send(apiSourceMsgs.source())
        .subscribe(
            tx -> {
              log.debug("ApiProducer sent WfMgmtRunMsg: {}", tx.get());
              tx.commit();
            });
  }

  @Bean
  OnDemandSource<WfMgmtRunMsg> source() {
    return new OnDemandSource<>("source");
  }

  @Bean
  Sender<WfMgmtRunMsg> sender(OnDemandSource<WfMgmtRunMsg> source) {
    return source;
  }

  Function<WfMgmtRunMsg, String> routingKeySelector() {
    return msg -> msg.getState().toString();
  }
}
