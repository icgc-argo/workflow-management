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

import static org.icgc.argo.workflow_management.streams.schema.RunState.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.icgc.argo.workflow_management.streams.schema.RunState;

/**
 * When updating a Run using a RunMsg state, we need to check whether that run is allowed to go into
 * that state. For example a CANCELLING run can become CANCELLED but never INITIALIZING. With all of
 * these rules, we can create a graph of allowed state transitions where the nodes are the state of
 * Run and the edges are the state in the msg See state transition graph here:
 * https://github.com/icgc-argo/workflow-management/blob/develop/docs/WES%20States%20and%20Transitions.png
 */
@UtilityClass
public class StateTransition {
  private static final Map<RunState, Set<RunState>> RUN_TO_INPUT_STATE_LOOKUP =
      Map.of(
          QUEUED, Set.of(INITIALIZING, CANCELING, CANCELED, SYSTEM_ERROR),
          INITIALIZING,
              Set.of(RUNNING, CANCELING, CANCELED, EXECUTOR_ERROR, SYSTEM_ERROR, COMPLETE),
          CANCELING, Set.of(CANCELED, EXECUTOR_ERROR, SYSTEM_ERROR),
          RUNNING, Set.of(SYSTEM_ERROR, EXECUTOR_ERROR, CANCELED, CANCELING, COMPLETE));

  /**
   * This function applies the rules of our state graph. It takes the current Run RunState and an
   * input RunState trying to change the run. Then it returns the next state or an empty optional if
   * no valid next state.
   *
   * @param currentState The current Runstate.
   * @param inputState The input RunState trying to change the current RunState.
   * @return Optional containing valid nextState or empty if there are none.
   */
  public static Optional<RunState> nextState(RunState currentState, RunState inputState) {
    if (currentState.equals(RunState.QUEUED) & inputState.equals(RunState.CANCELING)) {
      // For QUEUED and inputState of CANCELING, nextState is CANCELED
      return Optional.of(RunState.CANCELED);
    } else if (RUN_TO_INPUT_STATE_LOOKUP.get(currentState).contains(inputState)) {
      // For all other cases where currentState has a valid inputState, the nextState is the
      // inputState
      return Optional.of(inputState);
    } else {
      // No valid next state
      return Optional.empty();
    }
  }
}
