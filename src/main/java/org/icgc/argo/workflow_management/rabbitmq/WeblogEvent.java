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

import static javax.xml.bind.DatatypeConverter.parseDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import org.icgc.argo.workflow_management.rabbitmq.schema.EngineParams;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;

@Getter
public class WeblogEvent {
  private final String runId;
  private final RunState runState;
  private final String utcTime;

  public WeblogEvent(JsonNode event) {
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

    this.runId = runId;
    this.runState = runState;
    this.utcTime = event.get("utcTime").asText();
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

  public WfMgmtRunMsg asRunMsg() {
    val timeStamp = parseDateTime(utcTime).getTime().toInstant().toEpochMilli();
    return WfMgmtRunMsg.newBuilder()
        .setRunId(runId)
        .setState(runState)
        .setWorkflowEngineParams(EngineParams.newBuilder().build())
        .setTimestamp(timeStamp)
        .build();
  }
}
