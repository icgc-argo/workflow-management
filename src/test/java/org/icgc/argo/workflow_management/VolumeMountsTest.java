package org.icgc.argo.workflow_management;

import lombok.val;
import org.icgc.argo.workflow_management.util.VolumeMounts;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class VolumeMountsTest {

  @Test
  public void defaultConfigTest() {
    val volMounts = Set.of("pv-claim:/some/dir");

    assertEquals(
        Set.of("pv-claim:/some/dir"), VolumeMounts.extract(volMounts, "/some/dir/with/sub/dir"));
  }

  @Test
  public void multiDirTest() {
    val volMounts =
        Set.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3");

    assertEquals(
        Set.of("pv-claim-one:/test-dir-1"), VolumeMounts.extract(volMounts, "/test-dir-1/sub/dir"));

    assertEquals(
        Set.of("pv-claim-two:/test-dir-2"), VolumeMounts.extract(volMounts, "/test-dir-2/sub/dir"));

    assertEquals(
        Set.of("pv-claim-three:/test-dir-3"),
        VolumeMounts.extract(volMounts, "/test-dir-3/sub/dir"));
  }

  @Test
  public void multiMatchAllTest() {
    val volMounts =
        Set.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3");

    assertEquals(
        Set.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3"),
        VolumeMounts.extract(volMounts, Set.of("/test-dir-1", "/test-dir-2", "/test-dir-3")));
  }

  @Test
  public void multiMatchPartialTest() {
    val volMounts =
        Set.of("pv-claim-one:/test-dir-1", "pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3");

    assertEquals(
        Set.of("pv-claim-one:/test-dir-2", "pv-claim-one:/test-dir-3"),
        VolumeMounts.extract(volMounts, Set.of("/test-dir-2", "/test-dir-3")));
  }

  @Test
  public void testExtractFromPropertiesAndEngineParams() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    k8sProperties.setVolMounts(
        Set.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"));

    // set a different volume for all three properties
    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/test-dir-1/sub/dir")
            .projectDir("/test-dir-2/sub/dir")
            .workDir("/test-dir-3/sub/dir")
            .build();

    assertEquals(
        Set.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"),
        VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesNoMatchVolMountsDefault() {
    val k8sProperties = new NextflowProperties.K8sProperties();

    k8sProperties.setVolMounts(
        Set.of(
            "pv-claim-one:/test-dir-1", "pv-claim-two:/test-dir-2", "pv-claim-three:/test-dir-3"));

    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/some/dir/launch/dir")
            .projectDir("/some/dir/project/dir")
            .workDir("/some/dir/work/dir")
            .build();

    assertEquals(Set.of(), VolumeMounts.extract(k8sProperties, workflowEngineParams));
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

    assertEquals(Set.of(), VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }
}
