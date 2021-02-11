/*
 * Copyright (c) 2021 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.argo.workflow_management.rabbitmq;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.source.OnDemandSource;
import com.pivotal.rabbitmq.stream.Transaction;
import com.pivotal.rabbitmq.topology.ExchangeType;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.config.rabbitmq.RabbitSchemaConfig;
import org.icgc.argo.workflow_management.gatekeeper.service.GateKeeperService;
import org.icgc.argo.workflow_management.rabbitmq.schema.EngineParams;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
@Profile("gatekeeper")
@AutoConfigureAfter(RabbitSchemaConfig.class)
@Configuration
@RequiredArgsConstructor
public class GateKeeperStreamsConfig {
  @Value("${gatekeeper.producer.topology.queueName}")
  private String producerDefaultQueueName;

  @Value("${gatekeeper.producer.topology.topicExchangeName}")
  private String producerTopicExchangeName;

  @Value("${gatekeeper.consumer.topology.topicExchangeName}")
  private String consumerTopicExchangeName;

  @Value("${gatekeeper.consumer.topology.queueName}")
  private String consumerQueueName;

  private static final String ROUTING_KEY = "#";

  private final RabbitEndpointService rabbit;
  private final GateKeeperService service;

  private final OnDemandSource<WfMgmtRunMsg> gatekeeperSource = new OnDemandSource<>("gatekeeperSource");

  @Bean
  public Consumer<Object> weblogConsumer() {
    return obj -> {
      log.info("weblogConsumer recived: {}", obj);
      gatekeeperSource.send(
              WfMgmtRunMsg.newBuilder()
                      .setRunId("foofoo")
                      .setState(RunState.UNKNOWN)
                      .setWorkflowParamsJsonStr("")
                      .setWorkflowType("")
                      .setWorkflowTypeVersion("")
                      .setWorkflowEngineParams(EngineParams.newBuilder().build())
                      .setTimestamp(0L)
                      .build());
    };
  }

  public Flux<Transaction<WfMgmtRunMsg>> consumedAndValidatedMsgs() {
    val dlxName = consumerTopicExchangeName + "-dlx";
    val dlqName = consumerQueueName + "-dlq";
    return rabbit
        .declareTopology(
            topologyBuilder ->
                topologyBuilder
                    .declareExchange(dlxName)
                    .and()
                    .declareQueue(dlqName)
                    .boundTo(dlxName)
                    .and()
                    .declareExchange(consumerTopicExchangeName)
                    .type(ExchangeType.topic)
                    .and()
                    .declareQueue(consumerQueueName)
                    .boundTo(consumerTopicExchangeName, ROUTING_KEY)
                    .withDeadLetterExchange(dlxName))
        .createTransactionalConsumerStream(consumerQueueName, WfMgmtRunMsg.class)
        .receive()
        .doOnNext(tx -> log.info("Gatekeeper Received: " + tx.get()))
        .filter(
            tx -> {
              val msg = tx.get();
              val isUpdated = service.updateRunState(msg);
              if (!isUpdated) {
                tx.reject();
                log.info("Gatekeeper Rejected: {}", msg);
              }
              return isUpdated;
            })
        .onErrorContinue(handleError());
  }

  @Bean
  public Disposable gatekeeperProducer() {
    val dlxName = producerTopicExchangeName + "-dlx";
    val dlqName = producerDefaultQueueName + "-dlq";
    return rabbit
        .declareTopology(
            topologyBuilder ->
                topologyBuilder
                    .declareExchange(dlxName)
                    .and()
                    .declareQueue(dlqName)
                    .boundTo(dlxName)
                    .and()
                    .declareExchange(producerTopicExchangeName)
                    .type(ExchangeType.topic)
                    .and()
                    .declareQueue(producerDefaultQueueName)
                    .boundTo(producerTopicExchangeName, ROUTING_KEY)
                    .withDeadLetterExchange(dlxName))
        .createTransactionalProducerStream(WfMgmtRunMsg.class)
        .route()
        .toExchange(producerTopicExchangeName)
        .withRoutingKey(routingKeySelector())
        .then()
        .send(consumedAndValidatedMsgs())
        .onErrorContinue(handleError())
        .subscribe(
            tx -> {
              log.info("Gatekeeper Sent: {}", tx.get());
              tx.commit();
            });
  }


  @Bean
  public Disposable gatekeeperWeblogProducer() {
    val dlxName = consumerTopicExchangeName + "-dlx";
    val dlqName = consumerQueueName + "-dlq";
    return rabbit
                   .declareTopology(
                           topologyBuilder ->
                                   topologyBuilder
                                           .declareExchange(dlxName)
                                           .and()
                                           .declareQueue(dlqName)
                                           .boundTo(dlxName)
                                           .and()
                                           .declareExchange(consumerTopicExchangeName)
                                           .type(ExchangeType.topic)
                                           .and()
                                           .declareQueue(consumerQueueName)
                                           .boundTo(producerTopicExchangeName, ROUTING_KEY)
                                           .withDeadLetterExchange(dlxName))
                   .createTransactionalProducerStream(WfMgmtRunMsg.class)
                   .route()
                   .toExchange(producerTopicExchangeName)
                   .withRoutingKey(routingKeySelector())
                   .then()
                   .send(gatekeeperSource.source())
                   .onErrorContinue(handleError())
                   .subscribe(
                           tx -> {
                             log.info("GatekeeperWeblogProducer Sent: {}", tx.get());
                             tx.commit();
                           });
  }

  public BiConsumer<Throwable, Object> handleError() {
    return (t, tx) -> {
      t.printStackTrace();
      log.error("Error occurred with: {}", tx);
      if (tx instanceof Transaction<?> && ((Transaction<?>) tx).get() instanceof WfMgmtRunMsg) {
        val msg = (WfMgmtRunMsg) ((Transaction<?>) tx).get();
        msg.setState(RunState.SYSTEM_ERROR);
        log.info("SYSTEM_ERROR: {}", msg);
//         webLogEventSender.sendWfMgmtEventAsync(createWfMgmtEvent(msg));
        ((Transaction<?>) tx).reject();
      } else {
        log.error("Can't get WfMgmtRunMsg, transaction is lost!");
      }
    };
  }

  Function<WfMgmtRunMsg, String> routingKeySelector() {
    return msg -> msg.getState().toString();
  }
}
