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

package org.icgc.argo.workflow_management.gatekeeper.service;

import static org.icgc.argo.workflow_management.rabbitmq.schema.RunState.*;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.gatekeeper.model.ActiveRun;
import org.icgc.argo.workflow_management.gatekeeper.repository.ActiveRunsRepo;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("gatekeeper")
@Slf4j
@Service
@RequiredArgsConstructor
public class GateKeeperService {
  private static final Map<RunState, Set<RunState>> STATE_LOOKUP =
      Map.of(
          QUEUED, Set.of(INITIALIZING, CANCELING, CANCELED, SYSTEM_ERROR),
          INITIALIZING, Set.of(RUNNING, CANCELING, CANCELED, EXECUTOR_ERROR, SYSTEM_ERROR),
          CANCELING, Set.of(CANCELED, EXECUTOR_ERROR, SYSTEM_ERROR),
          RUNNING, Set.of(SYSTEM_ERROR, EXECUTOR_ERROR, CANCELED, CANCELING, COMPLETE));

  private static final Set<RunState> TERMINAL_STATES =
      Set.of(SYSTEM_ERROR, EXECUTOR_ERROR, CANCELED, COMPLETE);

  private final ActiveRunsRepo repo;

  /**
   * Checks if msg is valid next state for an active run. Returns msgs if allowed and null if not.
   */
  @Transactional
  public WfMgmtRunMsg checkWfMgmtRunMsgAndUpdate(WfMgmtRunMsg msg) {
    val knownRunOpt = repo.findById(msg.getRunId());

    // short circuit run is new case
    if (knownRunOpt.isEmpty() && msg.getState().equals(QUEUED)) {
      val newRun = repo.save(fromMsg(msg));
      log.debug("Active Run created: {}", newRun);
      return msg;
    } else if (knownRunOpt.isEmpty()) {
      return null;
    }

    val knownRun = knownRunOpt.get();
    val next = msg.getState();

    return fromActiveRun(checkActiveRunAndUpdate(knownRun, next));
  }

  @Transactional
  public WfMgmtRunMsg checkWithExistingAndUpdateStateOnly(String runId, RunState next) {
    val knownRunOpt = repo.findById(runId);
    if (knownRunOpt.isEmpty()) {
      log.debug("Active Run not found, so not updated: {} {}", runId, next);
      return null;
    } else {
      return fromActiveRun(checkActiveRunAndUpdate(knownRunOpt.get(), next));
    }
  }

  @Transactional
  private ActiveRun checkActiveRunAndUpdate(ActiveRun knownRun, RunState next) {
    val current = knownRun.getState();

    // special case if queued is going to canceling, just make it canceled
    next = current.equals(QUEUED) && next.equals(CANCELING) ? CANCELED : next;

    if (STATE_LOOKUP.getOrDefault(current, Set.of()).contains(next)) {
      if (TERMINAL_STATES.contains(next)) {
        repo.deleteById(knownRun.getRunId());
        log.debug("Active Run removed: {}", knownRun);
        return knownRun;
      } else {
        knownRun.setState(next);
        val updatedRun = repo.save(knownRun);
        log.debug("Active Run updated: {}", updatedRun);
        return updatedRun;
      }
    }

    return null;
  }

  public Page<ActiveRun> getRuns(Pageable pageable) {
    return getRuns(null, pageable);
  }

  public Page<ActiveRun> getRuns(Example<ActiveRun> example, Pageable pageable) {
    return example == null ? repo.findAll(pageable) : repo.findAll(example, pageable);
  }

  private ActiveRun fromMsg(WfMgmtRunMsg msg) {
    val msgWep = msg.getWorkflowEngineParams();
    val runWep =
        ActiveRun.EngineParams.builder()
            .latest(msgWep.getLatest())
            .defaultContainer(msgWep.getDefaultContainer())
            .launchDir(msgWep.getLaunchDir())
            .revision(msgWep.getRevision())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .resume(msgWep.getResume())
            .build();

    return ActiveRun.builder()
        .runId(msg.getRunId())
        .state(msg.getState())
        .workflowUrl(msg.getWorkflowUrl())
        .workflowParamsJsonStr(msg.getWorkflowParamsJsonStr())
        .workflowEngineParams(runWep)
        .timestamp(msg.getTimestamp())
        .build();
  }

  private WfMgmtRunMsg fromActiveRun(ActiveRun activeRun) {
    if (activeRun == null) return null;

    val msgWep = activeRun.getWorkflowEngineParams();
    val runWep =
        org.icgc.argo.workflow_management.rabbitmq.schema.EngineParams.newBuilder()
            .setLatest(msgWep.getLatest())
            .setDefaultContainer(msgWep.getDefaultContainer())
            .setLaunchDir(msgWep.getLaunchDir())
            .setRevision(msgWep.getRevision())
            .setProjectDir(msgWep.getProjectDir())
            .setWorkDir(msgWep.getWorkDir())
            .setResume(msgWep.getResume())
            .build();

    return WfMgmtRunMsg.newBuilder()
        .setRunId(activeRun.getRunId())
        .setState(activeRun.getState())
        .setWorkflowUrl(activeRun.getWorkflowUrl())
        .setWorkflowParamsJsonStr(activeRun.getWorkflowParamsJsonStr())
        .setWorkflowEngineParams(runWep)
        .setTimestamp(activeRun.getTimestamp())
        .build();
  }
}
