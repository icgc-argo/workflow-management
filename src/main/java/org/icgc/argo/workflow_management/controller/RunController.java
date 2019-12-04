package org.icgc.argo.workflow_management.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.Valid;
import lombok.val;
import org.icgc.argo.workflow_management.controller.model.RunRequest;
import org.icgc.argo.workflow_management.controller.model.RunResponse;
import org.icgc.argo.workflow_management.exception.model.ErrorResponse;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/runs")
@Api(value = "WorkflowExecutionService", tags = "WorkflowExecutionService")
public class RunController {

  @Autowired
  @Qualifier("nextflow")
  private WorkflowExecutionService nextflowService;

  @ApiOperation(
      value = "Run a workflow",
      nickname = "run",
      notes =
          "This endpoint creates a new workflow run and returns a RunId to monitor its progress.\n\n"
              + "The workflow_attachment array may be used to upload files that are required to execute the workflow, including the primary workflow, tools imported by the workflow, other files referenced by the workflow, or files which are part of the input. The implementation should stage these files to a temporary directory and execute the workflow from there. These parts must have a Content-Disposition header with a \"filename\" provided for each part. Filenames may include subdirectories, but must not include references to parent directories with '..' -- implementations should guard against maliciously constructed filenames.\n\n"
              + "The workflow_url is either an absolute URL to a workflow file that is accessible by the WES endpoint, or a relative URL corresponding to one of the files attached using workflow_attachment.\n\n"
              + "The workflow_params JSON object specifies input parameters, such as input files. The exact format of the JSON object depends on the conventions of the workflow language being used. Input files should either be absolute URLs, or relative URLs corresponding to files uploaded using workflow_attachment. The WES endpoint must understand and be able to access URLs supplied in the input. This is implementation specific.\n\n"
              + "The workflow_type is the type of workflow language and must be \"CWL\" or \"WDL\" currently (or another alternative supported by this WES instance).\n\n"
              + "The workflow_type_version is the version of the workflow language submitted and must be one supported by this WES instance.\n\n"
              + "See the RunRequest documentation for details about other fields.\n",
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
  @PostMapping
  private Mono<RunResponse> postRun(@Valid @RequestBody RunRequest runRequest) {

    val wesService = resolveWesType("nextflow");

    // create run config from request
    val runConfig =
        WESRunParams.builder()
            .workflowUrl(runRequest.getWorkflowUrl())
            .workflowParams(runRequest.getWorkflowParams())
            .workflowEngineParameters(runRequest.getWorkflowEngineParameters())
            .build();

    return wesService.run(runConfig);
  }

  private WorkflowExecutionService resolveWesType(String workflowType) {
    return nextflowService;
  }
}
