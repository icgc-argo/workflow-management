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

package org.icgc.argo.workflow_management.service;

import static org.icgc.argo.workflow_management.util.WesUtils.generateWesRunName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import lombok.val;
import org.icgc.argo.workflow_management.service.functions.CancelRunFunc;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WorkflowExecutionService {
  private final StartRunFunc startRunFunc;
  private final CancelRunFunc cancelRunFunc;

  @Autowired
  public WorkflowExecutionService(StartRunFunc startRunFunc, CancelRunFunc cancelRunFunc) {
    this.startRunFunc = startRunFunc;
    this.cancelRunFunc = cancelRunFunc;
  }

  @HasQueryAndMutationAccess
  public Mono<RunsResponse> run(RunsRequest runsRequest) {
    val params =
        RunParams.builder()
            .workflowUrl(runsRequest.getWorkflowUrl())
            .workflowParams(runsRequest.getWorkflowParams())
            .workflowEngineParams(runsRequest.getWorkflowEngineParams())
            .workflowType(runsRequest.getWorkflowType())
            .workflowTypeVersion(runsRequest.getWorkflowTypeVersion())
            .runName(generateWesRunName())
            .build();

    return startRunFunc.apply(params);
  }

  @HasQueryAndMutationAccess
  public Mono<RunsResponse> cancel(String runId) {
    return cancelRunFunc.apply(runId);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @PreAuthorize("@queryAndMutationScopeChecker.apply(authentication)")
  @interface HasQueryAndMutationAccess {}
}
