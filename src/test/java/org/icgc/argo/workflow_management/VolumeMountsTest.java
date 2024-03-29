package org.icgc.argo.workflow_management;

import static graphql.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import lombok.val;
import org.icgc.argo.workflow_management.util.VolumeMounts;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;
import org.junit.Test;

public class VolumeMountsTest {

  @Test
  public void defaultConfigTest() {
    val volMounts = List.of("pv-claim:/some/dir");

    assertEquals(
        List.of("pv-claim:/some/dir"), VolumeMounts.extract(volMounts, "/some/dir/with/sub/dir"));
  }

  @Test
  public void multiDirTest() {
    val volMounts =
        List.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3");

    assertEquals(
        List.of("pv-claim-one:/test-dir-1"),
        VolumeMounts.extract(volMounts, "/test-dir-1/sub/dir"));

    assertEquals(
        List.of("pv-claim-two:/test-dir-2"),
        VolumeMounts.extract(volMounts, "/test-dir-2/sub/dir"));

    assertEquals(
        List.of("pv-claim-three:/test-dir-3"),
        VolumeMounts.extract(volMounts, "/test-dir-3/sub/dir"));
  }

  @Test
  public void multiMatchAllTest() {
    val volMounts =
        List.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3");

    assertEquals(
        List.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3"),
        VolumeMounts.extract(volMounts, List.of("/test-dir-1", "/test-dir-2", "/test-dir-3")));
  }

  @Test
  public void multiMatchPartialTest() {
    val volMounts =
        List.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3");

    assertEquals(
        List.of("pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3"),
        VolumeMounts.extract(volMounts, List.of("/test-dir-2", "/test-dir-3")));
  }

  @Test
  public void multiMatchNoDuplicates() {
    val volMounts =
        List.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-1");

    assertEquals(
        List.of("pv-claim-one:/test-dir-1"),
        VolumeMounts.extract(volMounts, List.of("/test-dir-1", "/test-dir-1", "/test-dir-1")));
  }

  @Test
  public void testExtractFromPropertiesAndEngineParams() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    k8sProperties.setVolMounts(
        List.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"));

    // set a different volume for all three properties
    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/test-dir-1/sub/dir")
            .projectDir("/test-dir-2/sub/dir")
            .workDir("/test-dir-3/sub/dir")
            .build();

    assertEquals(
        List.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"),
        VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesMissingSomeEngineParams() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    k8sProperties.setVolMounts(
        List.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"));

    val workDirOnly = WorkflowEngineParams.builder().workDir("/test-dir-3/sub/dir").build();

    val projectDirOnly = WorkflowEngineParams.builder().projectDir("/test-dir-2/sub/dir").build();

    val launchDirOnly = WorkflowEngineParams.builder().launchDir("/test-dir-1/sub/dir").build();

    assertEquals(
        List.of("pv-claim-one:/test-dir-1", "pv-claim-three:/test-dir-3"),
        VolumeMounts.extract(k8sProperties, workDirOnly));

    assertEquals(
        List.of("pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2"),
        VolumeMounts.extract(k8sProperties, projectDirOnly));

    assertEquals(
        List.of("pv-claim-one:/test-dir-1"), VolumeMounts.extract(k8sProperties, launchDirOnly));
  }

  @Test
  public void testExtractFromPropertiesMissingAllEngineParams() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    k8sProperties.setVolMounts(
        List.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"));

    val workflowEngineParams = WorkflowEngineParams.builder().build();

    // when no engine params are provided, Nextflow will be default use the first volume claim in
    // the list provided, we need to ensure at least one claim is sent even when not explicitly set
    assertEquals(
        List.of("pv-claim-one:/test-dir-1"),
        VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesNoMatchVolMountsDefault() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    k8sProperties.setVolMounts(
        List.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"));

    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/some/dir/launch/dir")
            .projectDir("/some/dir/project/dir")
            .workDir("/some/dir/work/dir")
            .build();

    assertEquals(
        List.of("pv-claim-one:/test-dir-1"),
        VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesNullVolMountsDefault() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/some/dir/launch/dir")
            .projectDir("/some/dir/project/dir")
            .workDir("/some/dir/work/dir")
            .build();

    val exception =
        assertThrows(
            IllegalStateException.class,
            () -> VolumeMounts.extract(k8sProperties, workflowEngineParams));

    assertTrue(
        exception
            .getMessage()
            .contains(
                "At least one volMount must be configured in order for Nextflow to run in Kubernetes"));
  }
}
