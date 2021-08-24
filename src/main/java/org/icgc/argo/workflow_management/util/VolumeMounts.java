package org.icgc.argo.workflow_management.util;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
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
    return Optional.ofNullable(k8sProperties.getVolMountMappings())
        .map(
            volMountsMapping ->
                Stream.of(
                        workflowEngineParams.getLaunchDir(),
                        workflowEngineParams.getProjectDir(),
                        workflowEngineParams.getWorkDir())
                    .filter(Objects::nonNull)
                    .flatMap(path -> extract(volMountsMapping, path).stream())
                    .collect(Collectors.toSet()))
        .flatMap(volMounts -> volMounts.isEmpty() ? Optional.empty() : Optional.of(volMounts))
        .or(() -> Optional.ofNullable(k8sProperties.getVolMounts()))
        .orElse(Set.of());
  }
}
