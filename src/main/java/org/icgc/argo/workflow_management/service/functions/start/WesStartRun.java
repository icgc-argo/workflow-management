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

package org.icgc.argo.workflow_management.service.functions.start;

import lombok.RequiredArgsConstructor;
import org.icgc.argo.workflow_management.service.WebLogEventSender;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

/**
 * WorkflowStartRUn is responsible for selecting the correct engine run function and running it.
 * Currently only has one engine, Nextflow.
 */
@RequiredArgsConstructor
public class WesStartRun implements StartRunFunc {
  // For future, replace with ImmutableMap that maps workflowType & workflowTypeVersions to
  // appropriate engine startRunFuncs, for now just nextflow
  private final StartRunFunc defaultStartRunFunc;
  private final WebLogEventSender webLogEventSender;

  @Override
  public Mono<RunsResponse> apply(RunParams runParams) {
    // send message to relay saying run is initialized
    webLogEventSender.sendManagementEvent(runParams, WebLogEventSender.Event.INITIALIZED);
    return resolveStartRunFunc(runParams.getWorkflowType(), runParams.getWorkflowTypeVersion())
        .apply(runParams);
  }

  private StartRunFunc resolveStartRunFunc(String workflowType, String workflowTypeVersion) {
    return defaultStartRunFunc;
  }
}
