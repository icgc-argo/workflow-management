package org.icgc.argo.workflow_management.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.Valid;
import org.icgc.argo.workflow_management.controller.model.RunRequest;
import org.icgc.argo.workflow_management.controller.model.RunResponse;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

@Api(value = "WorkflowExecutionService", tags = "WorkflowExecutionService")
public interface RunApi {

  @ApiOperation(
      value = "Run a workflow",
      nickname = "run",
      notes =
          "This endpoint creates a new workflow run and returns a runId to monitor its progress.\n\n"
              + "The workflow_attachment is part of the GA4GH WES API Standard however we currently not supporting it as of this release.\n\n"
              + "The workflow_url is the workflow GitHub repository URL (ex. icgc-argo/nextflow-dna-seq-alignment) that is accessible by the WES endpoint.\n\n"
              + "The workflow_params JSON object specifies the input parameters for a workflow. The exact format of the JSON object depends on the conventions of the workflow.\n\n"
              + "The workflow_type is the type of workflow language, currently this WES API supports \"nextflow\" only.\n\n"
              + "The workflow_type_version is the version of the workflow language to run the workflow against and must be one supported by this WES instance.\n",
      response = RunResponse.class,
      tags = {
        "WorkflowExecutionService",
      })
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "", response = RunResponse.class),
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
  Mono<RunResponse> postRun(@Valid @RequestBody RunRequest runRequest);
}
