package org.icgc.argo.workflow_management.service.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "nextflow")
public class NextflowProperties {
  private K8sProperties k8s;
  private String weblogUrl;

  @Data
  public static class K8sProperties {
    private String namespace;
    private String volMounts;
  }
}
