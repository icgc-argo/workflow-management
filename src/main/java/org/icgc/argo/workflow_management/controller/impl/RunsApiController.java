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

package org.icgc.argo.workflow_management.controller.impl;

import javax.validation.Valid;
import lombok.val;
import org.icgc.argo.workflow_management.controller.RunsApi;
import org.icgc.argo.workflow_management.model.wes.RunsRequest;
import org.icgc.argo.workflow_management.model.wes.RunsResponse;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/runs")
public class RunsApiController implements RunsApi {

  /** Dependencies */
  private final WorkflowExecutionService nextflowService;

  @Autowired
  public RunsApiController(@Qualifier("nextflow") WorkflowExecutionService nextflowService) {
    this.nextflowService = nextflowService;
  }

  @PostMapping
  public Mono<RunsResponse> postRun(@Valid @RequestBody RunsRequest runsRequest) {

    val wesService = resolveWesType("nextflow");

    // create run config from request
    val runConfig =
        WESRunParams.builder()
            .workflowUrl(runsRequest.getWorkflowUrl())
            .workflowParams(runsRequest.getWorkflowParams())
            .workflowEngineParams(runsRequest.getWorkflowEngineParams())
            .build();

    return wesService.run(runConfig);
  }

  @PostMapping(
      path = "/{run_id}/cancel",
      produces = {"application/json"})
  public Mono<RunsResponse> cancelRun(@Valid @PathVariable("run_id") String runId) {
    val wesService = resolveWesType("nextflow");
    return wesService.cancel(runId);
  }

  // This method will eventually be responsible for which workflow service we run
  private WorkflowExecutionService resolveWesType(String workflowType) {
    return nextflowService;
  }
}
