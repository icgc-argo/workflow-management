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

package org.icgc.argo.workflow_management.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.Valid;
import org.icgc.argo.workflow_management.controller.model.wes.RunsRequest;
import org.icgc.argo.workflow_management.controller.model.wes.RunsResponse;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

@Api(value = "WorkflowExecutionService", tags = "WorkflowExecutionService")
public interface RunsApi {

  @ApiOperation(
      value = "Run a workflow",
      nickname = "runs",
      notes =
          "This endpoint creates a new workflow run and returns a runId to monitor its progress.\n\n"
              + "The workflow_attachment is part of the GA4GH WES API Standard however we currently not supporting it as of this release.\n\n"
              + "The workflow_url is the workflow GitHub repository URL (ex. icgc-argo/nextflow-dna-seq-alignment) that is accessible by the WES endpoint.\n\n"
              + "The workflow_params JSON object specifies the input parameters for a workflow. The exact format of the JSON object depends on the conventions of the workflow.\n\n"
              + "The workflow_engine_parameters JSON object specifies additional run-time arguments to the workflow engine (ie. specific workflow version, resuming a workflow, etc.)"
              + "The workflow_type is the type of workflow language, currently this WES API supports \"nextflow\" only.\n\n"
              + "The workflow_type_version is the version of the workflow language to run the workflow against and must be one supported by this WES instance.\n",
      response = RunsResponse.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "", response = RunsResponse.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 404,
            message = "The requested workflow run not found.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  Mono<RunsResponse> postRun(@Valid @RequestBody RunsRequest runsRequest);

  @ApiOperation(
      value = "Cancel a running workflow",
      nickname = "cancel run",
      notes = " ",
      response = RunsResponse.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "", response = RunsResponse.class),
        @ApiResponse(
            code = 401,
            message = "The request is unauthorized.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 403,
            message = "The requester is not authorized to perform this action.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 404,
            message = "The requested workflow run not found.",
            response = ErrorResponse.class),
        @ApiResponse(
            code = 500,
            message = "An unexpected error occurred.",
            response = ErrorResponse.class)
      })
  Mono<RunsResponse> cancelRun(@Valid @RequestBody String runId);
}
