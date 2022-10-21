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

package org.icgc.argo.workflow_management.streams;

import static org.icgc.argo.workflow_management.streams.DisposableManager.GATEKEEPER_PRODUCER;
import static org.icgc.argo.workflow_management.streams.utils.RabbitmqUtils.createTransConsumerStream;
import static org.icgc.argo.workflow_management.streams.utils.RabbitmqUtils.createTransProducerStream;
import static org.icgc.argo.workflow_management.streams.utils.WfMgmtRunMsgConverters.createWfMgmtEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.source.OnDemandSource;
import com.pivotal.rabbitmq.stream.Transaction;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.config.rabbitmq.RabbitSchemaConfig;
import org.icgc.argo.workflow_management.gatekeeper.service.GatekeeperProcessor;
import org.icgc.argo.workflow_management.streams.model.WeblogEvent;
import org.icgc.argo.workflow_management.streams.schema.RunState;
import org.icgc.argo.workflow_management.streams.schema.WfMgmtRunMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Profile("gatekeeper & !test")
@AutoConfigureAfter(RabbitSchemaConfig.class)
@Configuration
@RequiredArgsConstructor
public class GateKeeperStreamsConfig {
  private static final String ROUTING_KEY = "#";

  private static final Set<RunState> WEBLOG_EVENTS_OUTSIDE_MGMT =
      Set.of(RunState.COMPLETE, RunState.EXECUTOR_ERROR, RunState.RUNNING, RunState.SYSTEM_ERROR);

  private static final Set<RunState> RUN_STATES_TO_WEBLOG =
      Set.of(RunState.QUEUED, RunState.CANCELED);

  @Value("${gatekeeper.producer.topicExchange}")
  private String producerTopicExchangeName;

  @Value("${gatekeeper.consumer.topicExchange}")
  private String consumerTopicExchangeName;

  @Value("${gatekeeper.consumer.queue}")
  private String consumerQueueName;

  private final RabbitEndpointService rabbit;
  private final GatekeeperProcessor processor;
  private final WebLogEventSender webLogEventSender;
  private final DisposableManager disposableManager;

  private final OnDemandSource<WfMgmtRunMsg> weblogSourceSink =
      new OnDemandSource<>("weblogSourceSink");

  @PostConstruct
  public void init() {
    disposableManager.registerDisposable(GATEKEEPER_PRODUCER, this::createGatekeeperProducer);
  }

  /**
   * Disposable that takes input messages, passes them through gatekeeper processor and produces the
   * msgs that gatekeeper processor allowed to pass.
   */
  private Disposable createGatekeeperProducer() {
    val gatekeeperInputMsgsFlux = createGatekeeperInputFlux();

    val weblogInputMsgsFlux = weblogSourceSink.source();

    val processedFlux =
        processor
            .apply(gatekeeperInputMsgsFlux, weblogInputMsgsFlux)
            .flatMap(
                tx -> {
                  if (RUN_STATES_TO_WEBLOG.contains(tx.get().getState())) {
                    return webLogEventSender
                        .sendWfMgmtEvent(createWfMgmtEvent(tx.get()))
                        .thenReturn(tx);
                  }
                  return Mono.just(tx);
                })
            .onErrorContinue(handleError());
    log.debug("GateKeeperProducer Sending to: {}",producerTopicExchangeName);
    return createTransProducerStream(rabbit, producerTopicExchangeName)
        .send(processedFlux)
        .onErrorContinue(handleError())
        .subscribe(
            tx -> {
              log.debug("GateKeeperProducer Sent: {}", tx.get());
              tx.commit();
            });
  }

  /**
   * Functional bean consuming kafka weblog events from outside mgmt domain and sending messages for
   * to weblogSourceSink
   */
  @Bean
  public Consumer<JsonNode> weblogConsumer() {
    return event -> {
      val weblogEvent = new WeblogEvent(event);
      if (WEBLOG_EVENTS_OUTSIDE_MGMT.contains(weblogEvent.getRunState())) {
        log.debug("WeblogConsumer received: {}", event);
        weblogSourceSink.send(weblogEvent.asRunMsg());
      }
    };
  }

  /** Flux of input messages into gatekeeper */
  private Flux<Transaction<WfMgmtRunMsg>> createGatekeeperInputFlux() {
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
        webLogEventSender.sendWfMgmtEventAsync(createWfMgmtEvent(msg));
        ((Transaction<?>) tx).reject();
      } else {
        log.error("Can't get WfMgmtRunMsg, transaction is lost!");
      }
    };
  }
}
