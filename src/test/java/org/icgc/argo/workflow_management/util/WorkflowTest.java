package org.icgc.argo.workflow_management.util;

import java.io.IOException;
import java.util.Map;
import lombok.val;
import org.icgc.argo.workflow_management.controller.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.service.NextflowService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;

public class WorkflowTest {

  public static void main(String[] args) throws IOException {
    val p = new WorkflowEngineParams();
    val params =
        WESRunParams.builder()
            .workflowUrl("nextflow-io/hello")
            .workflowParams(Map.of())
            .workflowEngineParams(p)
            .build();
    runTest(params);
  }

  static void runTest(WESRunParams params) {
    NextflowProperties config = new NextflowProperties();
    val service = new NextflowService(config);
    val result = service.run(params);
    System.err.println(result.toString());
  }
}
