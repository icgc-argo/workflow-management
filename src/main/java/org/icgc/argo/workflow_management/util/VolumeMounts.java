package org.icgc.argo.workflow_management.util;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;

public class VolumeMounts {
  public static List<String> extract(
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
                        .collect(Collectors.toList())))
        .orElse(Collections.emptyList());
  }

  public static List<String> extract(@NonNull List<String> volMounts, @NonNull List<String> paths) {
    return volMounts.stream()
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
        // volMounts need to be provided as a list,
        // however we only care about distinct values
        .distinct()
        // when no engine params are provided, Nextflow will by default use the first volume claim
        // in the list provided, we need to ensure at least one claim is sent even when not
        // explicitly mentioned, meaning that if we are matching against and empty set (no engine
        // params in the request) we need to provide at least one volume mount for Nextflow to use
        // as the default
        .collect(
            Collectors.collectingAndThen(
                Collectors.toList(),
                matchedVolMounts ->
                    matchedVolMounts.isEmpty() ? List.of(volMounts.get(0)) : matchedVolMounts));
  }

  public static List<String> extract(@NonNull List<String> volMounts, @NonNull String path) {
    return extract(volMounts, List.of(path));
  }

  private static final Predicate<List<String>> isPathsEmpty = List::isEmpty;
}
