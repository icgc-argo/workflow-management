package org.icgc.argo.workflow_management;

import static org.junit.Assert.assertEquals;

import java.util.List;
import lombok.val;
import org.icgc.argo.workflow_management.util.VolumeMounts;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;
import org.junit.Test;

public class VolumeMountsTest {

  @Test
  public void defaultConfigTest() {
    val mappings = List.of("pv-claim:/some/dir,/some/dir/");

    assertEquals(
        List.of("pv-claim:/some/dir"), VolumeMounts.extract(mappings, "/some/dir/with/sub/dir"));
  }

  @Test
  public void multiDirTest() {
    val mappings =
        List.of(
            "pv-claim-one:/test-dir-1/,/dir-one/",
            "pv-claim-two:/test-dir-2/,/dir-two/",
            "pv-claim-three:/test-dir-3/,/dir-three/");

    assertEquals(
        List.of("pv-claim-one:/test-dir-1/"), VolumeMounts.extract(mappings, "/dir-one/test/dir"));

    assertEquals(
        List.of("pv-claim-two:/test-dir-2/"), VolumeMounts.extract(mappings, "/dir-two/test/dir"));

    assertEquals(
        List.of("pv-claim-three:/test-dir-3/"),
        VolumeMounts.extract(mappings, "/dir-three/test/dir"));
  }

  @Test
  public void multiMatchTest() {
    val mappings =
        List.of(
            "pv-claim-one:/test-dir-1/,/vol-dir/",
            "pv-claim-one:/test-dir-2/,/vol-dir/",
            "pv-claim-one:/test-dir-3/,/vol-dir/");

    assertEquals(
        List.of(
            "pv-claim-one:/test-dir-1/", "pv-claim-one:/test-dir-2/", "pv-claim-one:/test-dir-3/"),
        VolumeMounts.extract(mappings, "/vol-dir/sub/dir"));
  }

  @Test
  public void testExtractFromPropertiesWhenPresent() {
    val k8sProperties = new NextflowProperties.K8sProperties(List.of("pv-claim:/some/dir"));
    k8sProperties.setVolMountMappings(List.of("pv-claim:/some/dir,/some/dir/"));

    assertEquals(
        List.of("pv-claim:/some/dir"),
        VolumeMounts.extract(k8sProperties, "/some/dir/with/sub/dir"));
  }

  @Test
  public void testExtractFromPropertiesWhenMissingVolMountsMapping() {
    val k8sProperties = new NextflowProperties.K8sProperties(List.of("pv-claim:/some/dir"));
    k8sProperties.setVolMounts(List.of("pv-claim:/some/dir"));

    assertEquals(
        List.of("pv-claim:/some/dir"),
        VolumeMounts.extract(k8sProperties, "/some/dir/with/sub/dir"));
  }

  @Test
  public void testExtractFromPropertiesWhenMissingWorkdir() {
    val k8sProperties = new NextflowProperties.K8sProperties(List.of("pv-claim:/some/dir"));
    k8sProperties.setVolMountMappings(List.of("pv-claim:/some/dir,/some/dir/"));
    k8sProperties.setVolMounts(List.of("pv-claim:/some/dir"));

    assertEquals(List.of("pv-claim:/some/dir"), VolumeMounts.extract(k8sProperties, null));
  }
}
