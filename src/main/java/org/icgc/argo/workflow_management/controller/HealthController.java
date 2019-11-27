package org.icgc.argo.workflow_management.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/")
public class HealthController {

  @GetMapping
  private Mono getHealth() {
    return Mono.just("It's Alive!");
  }
}
