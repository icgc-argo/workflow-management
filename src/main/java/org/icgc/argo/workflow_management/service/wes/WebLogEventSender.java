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

package org.icgc.argo.workflow_management.service.wes;

import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;
import static org.icgc.argo.workflow_management.util.JacksonUtils.toJsonString;

import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import nextflow.Const;
import nextflow.extension.Bolts;
import org.icgc.argo.workflow_management.service.wes.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class WebLogEventSender {
  @Value("${nextflow.weblogUrl}")
  String endpoint;

  public void sendNextflowEventAsync(NextflowMetadata metadata, NextflowEvent event) {
    val msg =
        WorkflowEvent.builder()
            .runId(metadata.getWorkflow().getRunName())
            .runName(metadata.getWorkflow().getRunName())
            .event(event.toString())
            .utcTime(nowInUtc())
            .metadata(metadata)
            .build();

    sendHttpMessage(msg).subscribe();
  }

  public void sendWfMgmtEventAsync(RunParams params, WesState stateForEvent) {
    sendWfMgmtEvent(params, stateForEvent).subscribe();
  }

  public void sendWfMgmtEventAsync(String runId, WesState stateForEvent) {
    sendWfMgmtEvent(runId, stateForEvent).subscribe();
  }

  public void sendWfMgmtEventAsync(WfManagementEvent event) {
    sendWfMgmtEvent(event).subscribe();
  }

  public Mono<Boolean> sendWfMgmtEvent(RunParams params, WesState stateForEvent) {
    val event = WfManagementEvent.builder()
                        .runId(params.getRunId())
                        .workflowUrl(params.getWorkflowUrl())
                        .workflowEngineParams(params.getWorkflowEngineParams())
                        .workflowParams(params.getWorkflowParams())
                        .event(stateForEvent.getValue())
                        .utcTime(nowInUtc())
                        .build();

    return sendHttpMessage(event);
  }

  public Mono<Boolean> sendWfMgmtEvent(String runId, WesState stateForEvent) {
    val event =
        WfManagementEvent.builder()
            .runId(runId)
            .event(stateForEvent.getValue())
            .utcTime(nowInUtc())
            .build();

    return sendHttpMessage(event);
  }

  public Mono<Boolean> sendWfMgmtEvent(WfManagementEvent event) {
    return sendHttpMessage(event);
  }

  private Mono<Boolean> sendHttpMessage(Object jsonReadyObject) {
    return WebClient.create(endpoint)
        .post()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(toJsonString(jsonReadyObject))
        .retrieve()
        .toEntity(Boolean.class)
        .flatMap(
            res -> {
              // Don't want to proceed with stream if response from weblog is bad, so throw error
              if (!res.getStatusCode().is2xxSuccessful() || !Objects.equals(res.getBody(), true)) {
                log.debug("Event failed to send to or process in weblog!");
                return Mono.error(new Exception("Event failed to send to or process in weblog!"));
              }
              log.debug("Message sent to weblog: " + jsonReadyObject);
              return Mono.just(res.getBody());
            });
  }

  private String nowInUtc() {
    return Bolts.format(new Date(), Const.ISO_8601_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
  }
}
