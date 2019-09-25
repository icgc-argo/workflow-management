package org.icgc.argo.workflow_management.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface WorkflowExecutionService {
    class WESRunConfig {
        JsonNode workflow_params;
        String workflow_url;
    }

    String run(WESRunConfig params);

    String cancel(String runId);

    String getServiceInfo();
}
