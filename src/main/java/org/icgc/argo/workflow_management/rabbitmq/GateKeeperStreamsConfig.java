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

import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createWfMgmtEvent;
import static org.icgc.argo.workflow_management.util.RabbitmqUtils.createTransConsumerStream;
import static org.icgc.argo.workflow_management.util.RabbitmqUtils.createTransProducerStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.source.OnDemandSource;
import com.pivotal.rabbitmq.stream.Transaction;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.config.rabbitmq.RabbitSchemaConfig;
import org.icgc.argo.workflow_management.gatekeeper.service.GateKeeperService;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.service.wes.WebLogEventSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Profile("gatekeeper")
@AutoConfigureAfter(RabbitSchemaConfig.class)
@Configuration
@RequiredArgsConstructor
public class GateKeeperStreamsConfig {
  private static final String ROUTING_KEY = "#";

  private static final Set<RunState> WEBLOG_EVENTS_OUTSIDE_MGMT =
      Set.of(RunState.COMPLETE, RunState.EXECUTOR_ERROR, RunState.RUNNING, RunState.SYSTEM_ERROR);

  private static final Set<RunState> RUN_STATES_TO_WEBLOG =
      Set.of(RunState.QUEUED, RunState.CANCELED);

  @Value("${gatekeeper.producer.topology.queueName}")
  private String producerDefaultQueueName;

  @Value("${gatekeeper.producer.topology.topicExchangeName}")
  private String producerTopicExchangeName;

  @Value("${gatekeeper.consumer.topology.topicExchangeName}")
  private String consumerTopicExchangeName;

  @Value("${gatekeeper.consumer.topology.queueName}")
  private String consumerQueueName;

  private final RabbitEndpointService rabbit;
  private final GateKeeperService service;
  private final WebLogEventSender webLogEventSender;

  private final OnDemandSource<WfMgmtRunMsg> weblogSourceSink =
      new OnDemandSource<>("weblogSourceSink");

  /** Disposable that takes the input messages, checks if they are valid and allows them */
  @Bean
  public Disposable gatekeeperProducer() {
    val msgFluxFromGatekeeperInput = gatekeeperQueueConsumedAndCheckedMsgs();

    val msgFluxFromWeblog = weblogSourceSink.source();

    val outputFlux = Flux.merge(msgFluxFromGatekeeperInput, msgFluxFromWeblog);

    return createTransProducerStream(
            rabbit, producerTopicExchangeName, producerDefaultQueueName, ROUTING_KEY)
        .send(outputFlux)
        .onErrorContinue(handleError())
        .subscribe(
            tx -> {
              log.debug("GateKeeperProducer Sent: {}", tx.get());
              tx.commit();
            });
  }

  /**
   * Functional bean consuming weblog events outside mgmt domain, updating gatekeeper and sending
   * messages for gatekeeper to produce
   */
  @Bean
  public Consumer<JsonNode> weblogConsumer() {
    return event -> {
      val weblogEvent = toWeblogEvent(event);

      if (WEBLOG_EVENTS_OUTSIDE_MGMT.contains(weblogEvent.getRunState())) {
        log.debug("WeblogConsumer received: {}", event);
        // Weblog events will only change run state info in gatekeeper service, not other params
        val allowedMsg =
            service.checkWithExistingAndUpdateStateOnly(
                weblogEvent.getRunId(), weblogEvent.getRunState());
        if (allowedMsg != null) {
          log.debug("WeblogConsumer sending to gatekeeper producer: {}", allowedMsg);
          weblogSourceSink.send(allowedMsg);
        }
      }
    };
  }

  /** Flux of input messages into gatekeeper */
  private Flux<Transaction<WfMgmtRunMsg>> gatekeeperQueueConsumedAndCheckedMsgs() {
    return createTransConsumerStream(
            rabbit, consumerTopicExchangeName, consumerQueueName, ROUTING_KEY)
        .receive()
        .doOnNext(tx -> log.debug("GateKeeperConsumer Received: " + tx.get()))
        .<Transaction<WfMgmtRunMsg>>handle(
            (tx, sink) -> {
              val msg = tx.get();
              val allowedMsg = service.checkWfMgmtRunMsgAndUpdate(msg);

              if (allowedMsg == null) {
                tx.reject();
                log.debug("GateKeeperConsumer - Gatekeeper Rejected: {}", msg);
                return;
              }

              sink.next(tx.map(allowedMsg));
            })
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
  }

  private WeblogEvent toWeblogEvent(JsonNode event) {
    String runId = "";
    RunState runState = RunState.UNKNOWN;
    if (!event.path("metadata").path("workflow").isMissingNode()) {
      // NEXTFLOW event
      runId = event.get("runName").asText();

      val eventString = event.get("event").asText();
      val success = event.get("metadata").get("workflow").get("success").asBoolean();
      runState = fromNextflowEventAndSuccess(eventString, success);
    } else if (event.has("workflowUrl")
        && event.has("runId")
        && event.has("event")
        && event.has("utcTime")) {
      // WFMGMT event
      runId = event.get("runId").asText();

      val eventString = event.get("event").asText();
      runState = RunState.valueOf(eventString);
    }

    return new WeblogEvent(runId, runState);
  }

  private RunState fromNextflowEventAndSuccess(
      @NonNull String nextflowEvent, @NonNull boolean success) {
    if (nextflowEvent.equalsIgnoreCase("started")) {
      return RunState.RUNNING;
    } else if (nextflowEvent.equalsIgnoreCase("completed") && success) {
      return RunState.COMPLETE;
    } else if ((nextflowEvent.equalsIgnoreCase("completed") && !success)
        || nextflowEvent.equalsIgnoreCase("failed")
        || nextflowEvent.equalsIgnoreCase("error")) {
      return RunState.EXECUTOR_ERROR;
    } else {
      return RunState.UNKNOWN;
    }
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

  @lombok.Value
  static class WeblogEvent {
    String runId;
    RunState runState;
  }
}
