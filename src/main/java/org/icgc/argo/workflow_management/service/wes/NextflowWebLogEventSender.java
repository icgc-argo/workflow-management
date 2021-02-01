/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
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

import static org.icgc.argo.workflow_management.service.wes.NextflowWebLogEventSender.Event.*;
import static org.icgc.argo.workflow_management.util.JacksonUtils.toJsonString;

import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.val;
import nextflow.Const;
import nextflow.extension.Bolts;
import nextflow.trace.TraceRecord;
import nextflow.util.SimpleHttpClient;
import org.icgc.argo.workflow_management.service.wes.model.NextflowMetadata;
import org.icgc.argo.workflow_management.service.wes.model.WfManagementEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@AllArgsConstructor
@NoArgsConstructor
public class NextflowWebLogEventSender {
  private final SimpleHttpClient httpClient = new SimpleHttpClient();

  @Value("${nextflow.weblogUrl}")
  private String endpoint;

  public void sendStartEvent(NextflowMetadata meta) {
    sendWorkflowEvent(STARTED, meta);
  }

  public void sendCompletedEvent(NextflowMetadata meta) {
    sendWorkflowEvent(COMPLETED, meta);
  }

  public void sendProcessSubmitted(TraceRecord traceRecord) {
    sendTraceEvent(PROCESS_SUBMITTED, traceRecord);
  }

  public void sendProcessStarted(TraceRecord traceRecord) {
    sendTraceEvent(PROCESS_STARTED, traceRecord);
  }

  public void sendProcessCompleted(TraceRecord traceRecord) {
    sendTraceEvent(PROCESS_COMPLETED, traceRecord);
  }

  public void sendErrorEvent(TraceRecord traceRecord) {
    sendTraceEvent(ERROR, traceRecord);
  }

  public void sendFailedPodEvent(String podName) {
    val message = new HashMap<String, Object>();
    String time =
        Bolts.format(new Date(), Const.ISO_8601_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    message.put("runName", podName);
    message.put("event", FAILED.toString());
    message.put("utcTime", time);

    httpClient.sendHttpMessage(endpoint, toJsonString(message));
  }

  public void sendTraceEvent(Event event, TraceRecord traceRecord) {}

  public HashMap<String, Object> createMessage(Event event, String runName) {
    val message = new HashMap<String, Object>();
    String time =
        Bolts.format(new Date(), Const.ISO_8601_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    message.put("runName", runName);
    message.put("runId", "?");
    message.put("event", event.toString());
    message.put("utcTime", time);

    return message;
  }

  public void sendWorkflowEvent(Event event, NextflowMetadata meta) {
    httpClient.sendHttpMessage(endpoint, createWorkflowMessageJSON(event, meta));
  }

  public void sendWfMgmtEvent(WfManagementEvent event) {
    WebClient.create(endpoint).post().bodyValue(event).retrieve().toBodilessEntity().subscribe();
  }

  public String createWorkflowMessageJSON(Event event, NextflowMetadata logMessage) {
    val runName = logMessage.getWorkflow().getRunName();
    val message = createMessage(event, runName);

    message.put("metadata", logMessage);

    return toJsonString(message);
  }

  enum Event {
    STARTED,
    COMPLETED,
    PROCESS_SUBMITTED,
    PROCESS_STARTED,
    PROCESS_COMPLETED,
    ERROR,
    FAILED
  }
}
