package org.icgc.argo.workflow_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;

@EnableWebFlux
@SpringBootApplication
public class WorkflowManagementApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkflowManagementApplication.class, args);
  }
}
