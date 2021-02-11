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

package org.icgc.argo.workflow_management.service.api_to_wes.impl;

import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createWfMgmtRunMsg;
import static org.icgc.argo.workflow_management.util.WesUtils.generateWesRunId;

import com.pivotal.rabbitmq.source.Sender;
import lombok.val;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.service.api_to_wes.ApiToWesService;
import org.icgc.argo.workflow_management.wes.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Profile("api")
@Service
public class ApiProducerToWes implements ApiToWesService {

  private final Sender<WfMgmtRunMsg> sender;

  @Autowired
  public ApiProducerToWes(Sender<WfMgmtRunMsg> sender) {
    this.sender = sender;
  }

  @Override
  public Mono<RunsResponse> run(RunsRequest runsRequest) {
    val runId = generateWesRunId();
    val msg = createWfMgmtRunMsg(runId, runsRequest, RunState.QUEUED);
    return Mono.just(msg).flatMap(sender::send).map(o -> new RunsResponse(runId));
  }

  @Override
  public Mono<RunsResponse> cancel(String runId) {
    val event = createWfMgmtRunMsg(runId, RunState.CANCELING);
    return Mono.just(event).flatMap(sender::send).map(o -> new RunsResponse(runId));
  }
}
