package org.icgc.argo.workflow_management.controller.properties;

import lombok.Data;
import org.icgc.argo.workflow_management.controller.model.ServiceInfoResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "service-info")
// TODO: Move this to the events/logging service
public class ServiceInfoProperties {
  private String authInstructionsUrl;
  private String contactInfoUrl;
  private List<ServiceInfoResponse.WorkflowEngineParameter> defaultWorkflowEngineParameters;
  private List<String> supportedFilesystemProtocols;
  private String supportedWesVersions;
  private Map<String, Object> tags;
  private Map<String, Object> workflowEngineVersions;
  private Map<String, Object> workflowTypeVersions;
}
