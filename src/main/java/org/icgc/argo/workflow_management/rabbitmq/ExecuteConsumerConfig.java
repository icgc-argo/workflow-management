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

import static org.icgc.argo.workflow_management.rabbitmq.DisposableManager.EXECUTE_CONSUMER;
import static org.icgc.argo.workflow_management.rabbitmq.DisposableManager.GATEKEEPER_PRODUCER;
import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createRunParams;
import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createWfMgmtEvent;
import static org.icgc.argo.workflow_management.util.RabbitmqUtils.createTransConsumerStream;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.stream.Transaction;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.config.rabbitmq.RabbitSchemaConfig;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.service.wes.WebLogEventSender;
import org.icgc.argo.workflow_management.service.wes.WorkflowExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;

import javax.annotation.PostConstruct;

@Profile("execute")
@Slf4j
@AutoConfigureAfter(RabbitSchemaConfig.class)
@Configuration
@RequiredArgsConstructor
public class ExecuteConsumerConfig {
  @Value("${execute.consumer.topology.queueName}")
  private String queueName;

  @Value("${execute.consumer.topology.topicExchangeName}")
  private String topicExchangeName;

  @Value("${execute.consumer.topology.topicRoutingKeys}")
  private String[] topicRoutingKeys;

  private final WebLogEventSender webLogEventSender;
  private final WorkflowExecutionService wes;
  private final RabbitEndpointService rabbit;
  private final DisposableManager disposableManager;

  @PostConstruct
  public void init() {
    disposableManager.registerDisposable(EXECUTE_CONSUMER, this::createWfMgmtRunMsgForExecuteConsumer);
  }

  private Disposable createWfMgmtRunMsgForExecuteConsumer() {
    return createTransConsumerStream(rabbit, topicExchangeName, queueName, topicRoutingKeys)
        .receive()
        .doOnNext(consumeAndExecuteInitializeOrCancel())
        .onErrorContinue(handleError())
        .subscribe();
  }

  private Consumer<Transaction<WfMgmtRunMsg>> consumeAndExecuteInitializeOrCancel() {
    return tx -> {
      val msg = tx.get();
      log.debug("WfMgmtRunMsg received: {}", msg);

      if (msg.getState().equals(RunState.CANCELING)) {
        wes.cancel(msg.getRunId())
            .subscribe(
                runsResponse -> {
                  log.info("Cancelled: {}", runsResponse);
                  tx.commit();
                });
      } else if (msg.getState().equals(RunState.INITIALIZING)) {
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

  private BiConsumer<Throwable, Object> handleError() {
    return (t, tx) -> {
      t.printStackTrace();
      log.error("Error occurred with: {}", tx);
      if (tx instanceof Transaction<?> && ((Transaction<?>) tx).get() instanceof WfMgmtRunMsg) {
        val msg = (WfMgmtRunMsg) ((Transaction<?>) tx).get();
        msg.setState(RunState.SYSTEM_ERROR);
        log.info("SYSTEM_ERROR: {}", msg);
        webLogEventSender.sendWfMgmtEventAsync(createWfMgmtEvent(msg));
        ((Transaction<?>) tx).commit();
      } else {
        log.error("Can't get WfMgmtRunMsg, transaction is lost!");
      }
    };
  }
}
