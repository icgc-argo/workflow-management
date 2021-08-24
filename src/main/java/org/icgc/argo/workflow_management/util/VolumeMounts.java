package org.icgc.argo.workflow_management.util;

import lombok.NonNull;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VolumeMounts {
  public static Set<String> extract(@NonNull Set<String> volMounts, @NonNull String path) {
    return extract(volMounts, Set.of(path));
  }

  public static Set<String> extract(@NonNull Set<String> volMounts, @NonNull Set<String> paths) {
    return volMounts.stream()
        .filter(
            volMount ->
                paths.stream()
                    .anyMatch(
                        path ->
                            // evaluate that the path in the engine param starts with volume mount
                            // path, remembering the format ... (volume-claim:volume-mount-path)
                            Pattern.compile("^" + Pattern.quote(volMount.split(":")[1]))
                                .matcher(path)
                                .find()))
        .collect(Collectors.toSet());
  }

  public static Set<String> extract(
      NextflowProperties.K8sProperties k8sProperties, WorkflowEngineParams workflowEngineParams) {
    return Optional.ofNullable(k8sProperties.getVolMounts())
        .map(
            volMounts ->
                extract(
                    volMounts,
                    Stream.of(
                            workflowEngineParams.getLaunchDir(),
                            workflowEngineParams.getProjectDir(),
                            workflowEngineParams.getWorkDir())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())))
        .orElse(Set.of());
  }
}
