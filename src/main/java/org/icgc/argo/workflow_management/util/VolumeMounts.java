package org.icgc.argo.workflow_management.util;

import java.util.List;
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
  public static Set<String> extract(@NonNull List<String> volMounts, @NonNull String path) {
    return extract(volMounts, Set.of(path));
  }

  public static Set<String> extract(@NonNull List<String> volMounts, @NonNull Set<String> paths) {
    // when no engine params are provided, Nextflow will by default use the first volume claim in
    // the list provided, we need to ensure at least one claim is sent even when not explicitly
    // mentioned, meaning that if we are matching against and empty set (no engine params in the
    // request) we need to provide at least one volume mount for Nextflow to use as the default
    return paths.isEmpty()
        ? Set.of(volMounts.get(0))
        : volMounts.stream()
            .filter(
                volMount ->
                    paths.stream()
                        .anyMatch(
                            path ->
                                // evaluate that the path in the engine param starts with volume
                                // mount path, remembering the format ...
                                // (volume-claim:volume-mount-path)
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
