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

import static org.icgc.argo.workflow_management.util.RabbitmqUtils.createTransConsumerStream;
import static org.icgc.argo.workflow_management.util.RabbitmqUtils.createTransProducerStream;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.source.OnDemandSource;
import com.pivotal.rabbitmq.stream.Transaction;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

  private final OnDemandSource<WfMgmtRunMsg> weblogSink = new OnDemandSource<>("weblogSource");

  /** Creates functional bean to consume weblog kafka events */
  @Bean
  public Consumer<Object> weblogConsumer() {
    return obj -> {
      log.info("WeblogConsumer received from kafka: {}", obj);
      weblogSink.send(
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
  /**
   * Creates disposable to produce rabbit msg of weblog kafka event that were converted and sinked
   */
  @Bean
  public Disposable weblogSourceProducer() {
    return createTransProducerStream(
            rabbit, consumerTopicExchangeName, consumerQueueName, ROUTING_KEY)
        .send(weblogSink.source())
        .onErrorContinue(handleError())
        .subscribe(
            tx -> {
              log.info("WeblogSourceProducer Sent: {}", tx.get());
              tx.commit();
            });
  }

  /** Disposable that takes the input messages, checks if they are valid and allows them */
  @Bean
  public Disposable gatekeeperProcessor() {
    val inputFlux =
        consumedMsgs().doOnNext(tx -> log.debug("GateKeeperProcessor Received: " + tx.get()));

    Flux<Transaction<WfMgmtRunMsg>> outputFlux =
        inputFlux
            .filter(
                tx -> {
                  val msg = tx.get();
                  val isUpdated = service.updateRunState(msg);
                  if (!isUpdated) {
                    tx.reject();
                    log.debug("GateKeeperProcessor Rejected: {}", msg);
                  }
                  return isUpdated;
                })
            .onErrorContinue(handleError());

    return createTransProducerStream(
            rabbit, producerTopicExchangeName, producerDefaultQueueName, ROUTING_KEY)
        .send(outputFlux)
        .onErrorContinue(handleError())
        .subscribe(
            tx -> {
              log.debug("GateKeeperProcessor Sent: {}", tx.get());
              tx.commit();
            });
  }

  /** Flux of input messages into gatekeeper */
  private Flux<Transaction<WfMgmtRunMsg>> consumedMsgs() {
    return createTransConsumerStream(
            rabbit, consumerTopicExchangeName, consumerQueueName, ROUTING_KEY)
        .receive();
  }

  private BiConsumer<Throwable, Object> handleError() {
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
}
