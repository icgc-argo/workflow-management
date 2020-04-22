package org.icgc.argo.workflow_management.service.properties;

import java.util.List;
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
  private String masterUrl;
  private boolean trustCertificate;
  private long sleepInterval;
  private int maxErrorLogLines;

  @Data
  public static class K8sProperties {
    private Integer runAsUser;
    private String namespace;
    private List<String> volMounts;
    private String masterUrl;
    private boolean trustCertificate;
  }
}
