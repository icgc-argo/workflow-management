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

import static org.icgc.argo.workflow_management.gatekeeper.service.StateTransition.nextState;
import static org.icgc.argo.workflow_management.stream.schema.RunState.*;

import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.gatekeeper.model.Run;
import org.icgc.argo.workflow_management.gatekeeper.repository.ActiveRunsRepo;
import org.icgc.argo.workflow_management.stream.schema.EngineParams;
import org.icgc.argo.workflow_management.stream.schema.RunState;
import org.icgc.argo.workflow_management.stream.schema.WfMgmtRunMsg;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile({"gatekeeper-test", "gatekeeper"})
@Slf4j
@Service
@RequiredArgsConstructor
public class GateKeeperService {
  // Run moving into terminal states is removed from db because it's at the end of its lifecycle
  private static final Set<RunState> TERMINAL_STATES =
      Set.of(SYSTEM_ERROR, EXECUTOR_ERROR, CANCELED, COMPLETE);

  private final ActiveRunsRepo repo;

  /**
   * Checks if msg is moving run to a valid next state for an active run. Returns msgs with
   * nextState if allowed and null if not.
   */
  @Transactional
  public Optional<WfMgmtRunMsg> checkWfMgmtRunMsgAndUpdate(WfMgmtRunMsg msg) {
    val knownRunOpt = repo.findActiveRunByRunId(msg.getRunId());

    // short circuit, run is new
    if (knownRunOpt.isEmpty() && msg.getState().equals(QUEUED)) {
      val newRun = repo.save(fromMsg(msg));
      log.debug("Active Run created: {}", newRun);
      return Optional.of(msg);
    } else if (knownRunOpt.isEmpty()) {
      return Optional.empty();
    }

    val knownRun = knownRunOpt.get();
    val inputState = msg.getState();

    val msgWep = msg.getWorkflowEngineParams();
    val runWep =
        Run.EngineParams.builder()
            .latest(msgWep.getLatest())
            .defaultContainer(msgWep.getDefaultContainer())
            .launchDir(msgWep.getLaunchDir())
            .revision(msgWep.getRevision())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .resume(msgWep.getResume())
            .build();

    knownRun.setWorkflowEngineParams(runWep);
    knownRun.setWorkflowParamsJsonStr(msg.getWorkflowParamsJsonStr());

    return Optional.ofNullable(fromActiveRun(checkActiveRunAndUpdate(knownRun, inputState)));
  }

  /**
   * Checks if inputState is moving exsisting run to a valid next state. Returns msgs with nextState
   * if allowed and null if not.
   */
  @Transactional
  public Optional<WfMgmtRunMsg> checkWithExistingAndUpdateStateOnly(
      String runId, RunState inputState) {
    val knownRunOpt = repo.findActiveRunByRunId(runId);
    if (knownRunOpt.isEmpty()) {
      log.debug("Active Run not found, so not updated: {} {}", runId, inputState);
      return Optional.empty();
    } else {
      return Optional.ofNullable(
          fromActiveRun(checkActiveRunAndUpdate(knownRunOpt.get(), inputState)));
    }
  }

  @Transactional
  private Run checkActiveRunAndUpdate(Run knownRun, RunState inputState) {
    val currentState = knownRun.getState();

    // check if this is a valid state transition
    val nextStateOpt = nextState(currentState, inputState);
    if (nextStateOpt.isEmpty()) {
      return null;
    }

    val nextState = nextStateOpt.get();
    knownRun.setState(nextState);

    if (TERMINAL_STATES.contains(knownRun.getState())) {
      repo.deleteById(knownRun.getRunId());
      log.debug("Active Run removed: {}", knownRun);
      return knownRun;
    } else {
      val updatedRun = repo.save(knownRun);
      log.debug("Active Run updated: {}", updatedRun);
      return updatedRun;
    }
  }

  public Page<Run> getRuns(Pageable pageable) {
    return getRuns(null, pageable);
  }

  public Page<Run> getRuns(Example<Run> example, Pageable pageable) {
    return example == null ? repo.findAll(pageable) : repo.findAll(example, pageable);
  }

  private Run fromMsg(WfMgmtRunMsg msg) {
    val msgWep = msg.getWorkflowEngineParams();
    val runWep =
        Run.EngineParams.builder()
            .latest(msgWep.getLatest())
            .defaultContainer(msgWep.getDefaultContainer())
            .launchDir(msgWep.getLaunchDir())
            .revision(msgWep.getRevision())
            .projectDir(msgWep.getProjectDir())
            .workDir(msgWep.getWorkDir())
            .resume(msgWep.getResume())
            .build();

    return Run.builder()
        .runId(msg.getRunId())
        .state(msg.getState())
        .workflowUrl(msg.getWorkflowUrl())
        .workflowParamsJsonStr(msg.getWorkflowParamsJsonStr())
        .workflowEngineParams(runWep)
        .timestamp(msg.getTimestamp())
        .build();
  }

  private WfMgmtRunMsg fromActiveRun(Run run) {
    if (run == null) return null;

    val msgWep = run.getWorkflowEngineParams();
    val runWep =
        EngineParams.newBuilder()
            .setLatest(msgWep.getLatest())
            .setDefaultContainer(msgWep.getDefaultContainer())
            .setLaunchDir(msgWep.getLaunchDir())
            .setRevision(msgWep.getRevision())
            .setProjectDir(msgWep.getProjectDir())
            .setWorkDir(msgWep.getWorkDir())
            .setResume(msgWep.getResume())
            .build();

    return WfMgmtRunMsg.newBuilder()
        .setRunId(run.getRunId())
        .setState(run.getState())
        .setWorkflowUrl(run.getWorkflowUrl())
        .setWorkflowParamsJsonStr(run.getWorkflowParamsJsonStr())
        .setWorkflowEngineParams(runWep)
        .setTimestamp(run.getTimestamp())
        .build();
  }
}
