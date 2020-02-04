package org.icgc.argo.workflow_management;

import com.google.common.collect.Maps;
import lombok.val;
import nextflow.exception.IllegalDirectiveException;
import org.icgc.argo.workflow_management.controller.impl.RunsApiController;
import org.icgc.argo.workflow_management.controller.model.RunsRequest;
import org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver;
import org.icgc.argo.workflow_management.service.NextflowService;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.properties.NextflowProperties;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import static org.icgc.argo.workflow_management.exception.NextflowHttpStatusResolver.resolveHttpStatus;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = RunsApiController.class)
@Import(value = {
    NextflowService.class,
    NextflowProperties.class
})
public class WorkflowManagementApplicationTests {

  @Autowired
  private WebTestClient webClient;

  @Autowired
  private NextflowService nextflowService;

  @Test
  public void contextLoads() {}

  @Test
  public void testErrorHandling(){
    val req = new RunsRequest();
    req.setWorkflowUrl("sdf");
    req.setWorkflowParams(Maps.newHashMap());
    val s = Mockito.spy(nextflowService);
    given(s.run(Mockito.any())).willAnswer(invocation -> {throw new IllegalDirectiveException();} );
    val expectedHttpStatus = resolveHttpStatus(IllegalDirectiveException.class).get();

    webClient.post()
        .uri("/runs")
//        .contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromObject(req))
        .exchange()
        .expectStatus()
        .isEqualTo(expectedHttpStatus);

  }
}
