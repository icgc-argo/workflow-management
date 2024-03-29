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

import static org.icgc.argo.workflow_management.streams.DisposableManager.WES_CONSUMER;
import static org.icgc.argo.workflow_management.streams.utils.RabbitmqUtils.createTransConsumerStream;
import static org.icgc.argo.workflow_management.streams.utils.WfMgmtRunMsgConverters.createRunParams;
import static org.icgc.argo.workflow_management.streams.utils.WfMgmtRunMsgConverters.createWfMgmtEvent;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.stream.Transaction;
import java.time.Duration;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.config.rabbitmq.RabbitSchemaConfig;
import org.icgc.argo.workflow_management.streams.schema.RunState;
import org.icgc.argo.workflow_management.streams.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.wes.WorkflowExecutionService;
import org.icgc.argo.workflow_management.wes.model.WesState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetrySpec;

@Slf4j
@Profile("!test")
@AutoConfigureAfter(RabbitSchemaConfig.class)
@Configuration
@RequiredArgsConstructor
public class WesConsumerConfig {
  @Value("${wes.consumer.queue}")
  private String queueName;

  @Value("${wes.consumer.topicExchange}")
  private String topicExchangeName;

  @Value("${wes.consumer.topicRoutingKeys}")
  private String[] topicRoutingKeys;

  private final WebLogEventSender webLogEventSender;
  private final WorkflowExecutionService wes;
  private final RabbitEndpointService rabbit;
  private final DisposableManager disposableManager;

  @PostConstruct
  public void init() {
    disposableManager.registerDisposable(WES_CONSUMER, this::createWfMgmtRunMsgForExecuteConsumer);
  }

  private Disposable createWfMgmtRunMsgForExecuteConsumer() {
    return createTransConsumerStream(rabbit, topicExchangeName, queueName, topicRoutingKeys)
        .receive()
        // consume each tx msg and flatMap into publisher of Mono<Boolean>.
        // Mono<Boolean> is used so reactor can manage subscriptions and publisher signals.
        .flatMap(this::consumeMessageAndExecuteInitializeOrCancel)
        .log(WES_CONSUMER)
        .subscribe();
  }

  private Mono<Boolean> consumeMessageAndExecuteInitializeOrCancel(Transaction<WfMgmtRunMsg> tx) {
    log.debug("Message received from: {} {} {}", topicExchangeName, queueName, topicRoutingKeys);
    val msg = tx.get();
    log.debug("WfMgmtRunMsg received: {}", msg);

    if (msg.getState().equals(RunState.INITIALIZING)) {
      val params = createRunParams(msg);
      return webLogEventSender
          .sendWfMgmtEvent(params, WesState.INITIALIZING)
          .flatMap(res -> wes.run(params))
          .flatMap(runsResponse -> commitTx("Initialized", tx))
          .onErrorResume(t -> rejectAndWeblogTx(t, tx));
    } else if (msg.getState().equals(RunState.CANCELING)) {
      val runId = msg.getRunId();
      return webLogEventSender
          .sendWfMgmtEvent(runId, WesState.CANCELING)
          .flatMap(res -> wes.cancel(runId))
          .retryWhen(RetrySpec.backoff(3, Duration.ofMinutes(3)))
          .flatMap(runsResponse -> commitTx("Cancelled", tx))
          .onErrorResume(t -> rejectAndWeblogTx(t, tx));
    } else {
      return commitTx("Ignored", tx);
    }
  }

  private Mono<Boolean> commitTx(String actionMsg, Transaction<WfMgmtRunMsg> tx) {
    log.info(actionMsg, tx.get());
    tx.commit();
    return Mono.just(true);
  }

  private Mono<Boolean> rejectAndWeblogTx(Throwable t, Transaction<WfMgmtRunMsg> tx) {
    val msg = tx.get();
    msg.setState(RunState.SYSTEM_ERROR);

    log.error("Error occurred", t);
    log.error("WES SYSTEM_ERROR msg: {}", msg);
    log.debug("Error occurred", t);
    log.debug("WES SYSTEM_ERROR msg: {}", msg);
    tx.reject();

    return webLogEventSender
        .sendWfMgmtEvent(createWfMgmtEvent(msg))
        // onError here means failed to weblog the SYSTEM_ERROR
        // not much left to do if can't weblog SYSTEM_ERROR
        .onErrorReturn(false)
        .thenReturn(false);
  }
}
