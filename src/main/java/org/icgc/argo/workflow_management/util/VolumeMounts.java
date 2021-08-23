package org.icgc.argo.workflow_management.util;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;

public class VolumeMounts {
  public static List<String> extract(@NonNull List<String> mappings, @NonNull String workDir) {
    return mappings.stream()
        .filter(
            mapping ->
                Pattern.compile(Pattern.quote(mapping.split(",")[1])).matcher(workDir).find())
        .map(matchingMapping -> matchingMapping.split(",")[0])
        .collect(Collectors.toList());
  }

  public static List<String> extract(
      NextflowProperties.K8sProperties k8sProperties, String workDir) {
    return Optional.ofNullable(workDir)
        .flatMap(
            presentWorkDir ->
                Optional.ofNullable(k8sProperties.getVolMountMappings())
                    .map(volMountsMapping -> extract(volMountsMapping, workDir)))
        .orElse(k8sProperties.getVolMounts());
  }
}
