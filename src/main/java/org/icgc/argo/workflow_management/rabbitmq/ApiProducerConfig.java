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
import com.pivotal.rabbitmq.source.Sender;
import com.pivotal.rabbitmq.source.Source;
import com.pivotal.rabbitmq.topology.ExchangeType;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
    val dlxName = topicExchangeName + "-dlx";
    val dlqName = queueName + "-dlq";
    return rabbit
        .declareTopology(
            topologyBuilder ->
                topologyBuilder
                    .declareExchange(dlxName)
                    .and()
                    .declareQueue(dlqName)
                    .boundTo(dlxName)
                    .and()
                    .declareExchange(topicExchangeName)
                    .type(ExchangeType.topic)
                    .and()
                    .declareQueue(queueName)
                    .boundTo(topicExchangeName, topicRoutingKeys)
                    .withDeadLetterExchange(dlxName))
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
