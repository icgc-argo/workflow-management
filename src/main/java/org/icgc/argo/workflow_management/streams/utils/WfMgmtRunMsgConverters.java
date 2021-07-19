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

import static org.icgc.argo.workflow_management.util.JacksonUtils.readValue;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import lombok.val;
import nextflow.Const;
import nextflow.extension.Bolts;
import org.icgc.argo.workflow_management.streams.model.WfManagementEvent;
import org.icgc.argo.workflow_management.streams.schema.EngineParams;
import org.icgc.argo.workflow_management.streams.schema.RunState;
import org.icgc.argo.workflow_management.streams.schema.WfMgmtRunMsg;
import org.icgc.argo.workflow_management.wes.model.RunParams;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;

@UtilityClass
public class WfMgmtRunMsgConverters {

  public static WfMgmtRunMsg createWfMgmtRunMsg(String runId, RunState state) {
    return WfMgmtRunMsg.newBuilder()
        .setRunId(runId)
        .setTimestamp(Instant.now().toEpochMilli())
        .setState(state)
        .setWorkflowEngineParams(EngineParams.newBuilder().build())
        .build();
  }

  public static RunParams createRunParams(WfMgmtRunMsg msg) {
    val msgWep = msg.getWorkflowEngineParams();

    val params = readValue(msg.getWorkflowParamsJsonStr(), Map.class);

    val wepBuilder =
        WorkflowEngineParams.builder()
            .defaultContainer(msgWep.getDefaultContainer())
            .revision(msgWep.getRevision())
            .launchDir(msgWep.getLaunchDir())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .latest(msgWep.getLatest());

    if (msgWep.getResume() != null) {
      wepBuilder.resume(UUID.fromString(msgWep.getResume()));
    }

    return RunParams.builder()
        .runId(msg.getRunId())
        .workflowParams(params)
        .workflowEngineParams(wepBuilder.build())
        .workflowUrl(msg.getWorkflowUrl())
        .build();
  }

  public static WfManagementEvent createWfMgmtEvent(WfMgmtRunMsg msg) {
    val msgWep = msg.getWorkflowEngineParams();

    val params =
        msg.getWorkflowParamsJsonStr() != null
            ? readValue(msg.getWorkflowParamsJsonStr(), Map.class)
            : Map.of();

    val wepBuilder =
        WorkflowEngineParams.builder()
            .defaultContainer(msgWep.getDefaultContainer())
            .revision(msgWep.getRevision())
            .launchDir(msgWep.getLaunchDir())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .latest(msgWep.getLatest());

    if (msgWep.getResume() != null) {
      wepBuilder.resume(UUID.fromString(msgWep.getResume()));
    }

    String time =
        Bolts.format(
            new Date(msg.getTimestamp()),
            Const.ISO_8601_DATETIME_FORMAT,
            TimeZone.getTimeZone("UTC"));
    return WfManagementEvent.builder()
        .event(msg.getState().toString())
        .runId(msg.getRunId())
        .utcTime(time)
        .workflowEngineParams(wepBuilder.build())
        .workflowParams(params)
        .workflowType(msg.getWorkflowType())
        .workflowTypeVersion(msg.getWorkflowTypeVersion())
        .workflowUrl(msg.getWorkflowUrl())
        .build();
  }
}
