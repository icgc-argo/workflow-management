package org.icgc.argo.workflow_management;

import static org.junit.Assert.assertEquals;

import java.util.Set;
import lombok.val;
import org.icgc.argo.workflow_management.util.VolumeMounts;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;
import org.junit.Test;

public class VolumeMountsTest {

  @Test
  public void defaultConfigTest() {
    val mappings = Set.of("pv-claim:/some/dir,/some/dir/");

    assertEquals(
        Set.of("pv-claim:/some/dir"), VolumeMounts.extract(mappings, "/some/dir/with/sub/dir"));
  }

  @Test
  public void multiDirTest() {
    // include some inconsistent matching strings (with and without /)
    val mappings =
        Set.of(
            "pv-claim-one:/test-dir-1/,dir-one",
            "pv-claim-two:/test-dir-2/,/dir-two",
            "pv-claim-three:/test-dir-3/,dir-three/");

    assertEquals(
        Set.of("pv-claim-one:/test-dir-1/"), VolumeMounts.extract(mappings, "/dir-one/test/dir"));

    assertEquals(
        Set.of("pv-claim-two:/test-dir-2/"), VolumeMounts.extract(mappings, "/dir-two/test/dir"));

    assertEquals(
        Set.of("pv-claim-three:/test-dir-3/"),
        VolumeMounts.extract(mappings, "/dir-three/test/dir"));
  }

  @Test
  public void multiMatchTest() {
    val mappings =
        Set.of(
            "pv-claim-one:/test-dir-1/,/vol-dir/",
            "pv-claim-one:/test-dir-2/,/vol-dir/",
            "pv-claim-one:/test-dir-3/,/vol-dir/");

    assertEquals(
        Set.of(
            "pv-claim-one:/test-dir-1/", "pv-claim-one:/test-dir-2/", "pv-claim-one:/test-dir-3/"),
        VolumeMounts.extract(mappings, "/vol-dir/sub/dir"));
  }

  @Test
  public void testExtractFromPropertiesAndEngineParams() {
    val k8sProperties = new NextflowProperties.K8sProperties();
    k8sProperties.setVolMounts(Set.of("pv-claim:/some/dir"));

    // include some inconsistent matching strings (with and without /)
    k8sProperties.setVolMountMappings(
        Set.of(
            "pv-claim-one:/test-dir-1/,/dir-one/",
            "pv-claim-two:/test-dir-2/,/dir-two",
            "pv-claim-three:/test-dir-3/,dir-three/"));

    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/dir-one/test/dir")
            .projectDir("/dir-two/test/dir")
            .workDir("/dir-three/test/dir")
            .build();

    assertEquals(
        Set.of(
            "pv-claim-one:/test-dir-1/",
            "pv-claim-two:/test-dir-2/",
            "pv-claim-three:/test-dir-3/"),
        VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesWhenMissingVolMountsMapping() {
    val k8sProperties = new NextflowProperties.K8sProperties();
    k8sProperties.setVolMounts(Set.of("pv-claim:/some/dir"));

    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/some/dir/launch/dir")
            .projectDir("/some/dir/project/dir")
            .workDir("/some/dir/work/dir")
            .build();

    assertEquals(
        Set.of("pv-claim:/some/dir"), VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesWhenMissingDirectories() {
    val k8sProperties = new NextflowProperties.K8sProperties();
    k8sProperties.setVolMounts(Set.of("pv-claim:/some/dir"));
    k8sProperties.setVolMountMappings(Set.of("pv-claim:/some/dir,/some/dir/"));

    val workflowEngineParams = WorkflowEngineParams.builder().build();

    assertEquals(
        Set.of("pv-claim:/some/dir"), VolumeMounts.extract(k8sProperties, workflowEngineParams));
  }

  @Test
  public void testExtractFromPropertiesDefaultVolMountsOnNoMatch() {
    val k8sProperties = new NextflowProperties.K8sProperties();
    k8sProperties.setVolMounts(Set.of("pv-claim:/some/dir"));

    val workflowEngineParams =
        WorkflowEngineParams.builder()
            .launchDir("/different/dir/launch/dir")
            .projectDir("/different/dir/project/dir")
            .workDir("/different/dir/work/dir")
            .build();

    assertEquals(
        Set.of("pv-claim:/some/dir"), VolumeMounts.extract(k8sProperties, workflowEngineParams));
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
