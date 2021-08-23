package org.icgc.argo.workflow_management.util;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.val;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;

public class VolumeMounts {
  public static Set<String> extract(@NonNull Set<String> mappings, @NonNull String path) {
    return mappings.stream()
        .filter(
            mapping -> Pattern.compile(Pattern.quote(mapping.split(",")[1])).matcher(path).find())
        .map(matchingMapping -> matchingMapping.split(",")[0])
        .collect(Collectors.toSet());
  }

  public static Set<String> extract(
      NextflowProperties.K8sProperties k8sProperties, WorkflowEngineParams workflowEngineParams) {
    val volMounts =
        Stream.of(
                workflowEngineParams.getLaunchDir(),
                workflowEngineParams.getProjectDir(),
                workflowEngineParams.getWorkDir())
            .filter(Objects::nonNull)
            .flatMap(
                path ->
                    Optional.ofNullable(k8sProperties.getVolMountMappings())
                        .map(volMountsMapping -> extract(volMountsMapping, path))
                        .orElse(Set.copyOf(k8sProperties.getVolMounts())).stream())
            .collect(Collectors.toSet());

    return volMounts.isEmpty() ? k8sProperties.getVolMounts() : volMounts;
  }
}
