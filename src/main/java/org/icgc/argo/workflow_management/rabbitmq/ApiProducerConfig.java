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

import static org.icgc.argo.workflow_management.rabbitmq.DisposableManager.API_PRODCUER;
import static org.icgc.argo.workflow_management.rabbitmq.DisposableManager.EXECUTE_CONSUMER;
import static org.icgc.argo.workflow_management.util.RabbitmqUtils.createTransProducerStream;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.source.OnDemandSource;
import com.pivotal.rabbitmq.source.Sender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.config.rabbitmq.RabbitSchemaConfig;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.Disposable;

import javax.annotation.PostConstruct;

@Slf4j
@Profile("api")
@AutoConfigureAfter(RabbitSchemaConfig.class)
@Configuration
@RequiredArgsConstructor
public class ApiProducerConfig {
  @Value("${api.producer.topology.queueName}")
  private String queueName;

  @Value("${api.producer.topology.topicExchangeName}")
  private String topicExchangeName;

  @Value("${api.producer.topology.topicRoutingKeys}")
  private String[] topicRoutingKeys;

  private final RabbitEndpointService rabbit;
  private final DisposableManager disposableManager;
  private final OnDemandSource<WfMgmtRunMsg> sink = new OnDemandSource<>("apiSource");

  @PostConstruct
  public void init() {
    disposableManager.registerDisposable(API_PRODCUER, this::createWfMgmtRunMsgProducer);
  }

  private Disposable createWfMgmtRunMsgProducer() {
    return createTransProducerStream(rabbit, topicExchangeName, queueName, topicRoutingKeys)
        .send(sink.source())
        .subscribe(
            tx -> {
              log.debug("ApiProducer sent WfMgmtRunMsg: {}", tx.get());
              tx.commit();
            });
  }

  @Bean
  Sender<WfMgmtRunMsg> sender() { return sink; }
}
