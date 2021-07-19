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

package org.icgc.argo.workflow_management.streams.utils;

import com.pivotal.rabbitmq.RabbitEndpointService;
import com.pivotal.rabbitmq.stream.TransactionalConsumerStream;
import com.pivotal.rabbitmq.stream.TransactionalProducerStream;
import com.pivotal.rabbitmq.topology.ExchangeType;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.icgc.argo.workflow_management.streams.schema.WfMgmtRunMsg;

@UtilityClass
public class RabbitmqUtils {

  public static TransactionalProducerStream<WfMgmtRunMsg> createTransProducerStream(
      RabbitEndpointService rabbit, String topicName) {
    return rabbit
        .declareTopology(
            topologyBuilder ->
                topologyBuilder
                    .declareExchange(topicName)
                    .type(ExchangeType.topic))
        .createTransactionalProducerStream(WfMgmtRunMsg.class)
        .route()
        .toExchange(topicName)
        .withRoutingKey(routingKeySelector())
        .then();
  }

  public static TransactionalConsumerStream<WfMgmtRunMsg> createTransConsumerStream(
      RabbitEndpointService rabbit, String topicName, String queueName, String... routingKey) {
    val dlxName = queueName + "-dlx";
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
                    .declareExchange(topicName)
                    .type(ExchangeType.topic)
                    .and()
                    .declareQueue(queueName)
                    .boundTo(topicName, routingKey)
                    .withDeadLetterExchange(dlxName))
        .createTransactionalConsumerStream(queueName, WfMgmtRunMsg.class);
  }

  Function<WfMgmtRunMsg, String> routingKeySelector() {
    return msg -> msg.getState().toString();
  }
}
