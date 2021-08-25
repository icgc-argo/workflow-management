package org.icgc.argo.workflow_management.util;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;

public class VolumeMounts {
  private static final HashMap<String, Pattern> volMountPatternCache = new HashMap<>();

  /**
   * Given the k8sProperties configured in application properties, and the workflow engine
   * parameters for a run, extract the required volume mounts
   *
   * @param k8sProperties - properties containing the configured volume mounts
   * @param workflowEngineParams - the workflow engine parameters for the run
   * @return a list of volume mounts that are required for the engine parameters for the run
   */
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
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "At lease on volMount must be configured in order for Nextflow to run in Kubernetes"));
  }

  /**
   * Given a list of volMounts and paths, returns the volMounts required for the paths provided
   * where a volMount (with the format claim-name:mount-path) is considered required if the path
   * starts with the volume mount path. If no matches are found and there is at least one volMount,
   * the first volMount will be returned as the single item in a list as at least one volMount is
   * required
   *
   * @param volMounts - list of volMounts to search through
   * @param paths - list of paths to use as the search subject
   * @return the matching volMounts given the search
   */
  public static List<String> extract(@NonNull List<String> volMounts, @NonNull List<String> paths) {
    return volMounts.stream()
        .filter(volMount -> paths.stream().anyMatch(getPathMatchFunctionForVolMount(volMount)))
        // volMounts need to be provided as a list,
        // however we only care about distinct values
        .distinct()
        .collect(
            Collectors.collectingAndThen(
                Collectors.toList(), getDefaultVolMountIfEmptyFunctionForVolMount(volMounts)));
  }

  /**
   * Utility overload method that calls extract(@NonNull List<String> volMounts, @NonNull
   * List<String> paths)
   *
   * @param volMounts - list of volMounts to search through
   * @param path - the path to use as the search subject
   * @return the matching volMounts given the search
   */
  public static List<String> extract(@NonNull List<String> volMounts, @NonNull String path) {
    return extract(volMounts, List.of(path));
  }

  /**
   * Generates a function for matching path to volume mount
   *
   * @param volMount a single volMount entry from the list in application properties
   * @return a function which evaluates that the path provided matches the pattern for the volMount
   *     used to generate this function
   */
  private static Predicate<String> getPathMatchFunctionForVolMount(String volMount) {
    return path -> getPatternForVolMount(volMount).matcher(path).find();
  }

  /**
   * Lazily computes a pattern for each volume mount (given the format
   * volume-claim:volume-mount-path) in the application properties, backed by the static
   * patternCache HashMap
   *
   * @param volMount - the volume mount to extract the pattern for
   * @return the Pattern for the provided volMount string
   */
  private static Pattern getPatternForVolMount(String volMount) {
    return volMountPatternCache.computeIfAbsent(
        volMount, key -> Pattern.compile("^" + Pattern.quote(volMount.split(":")[1])));
  }

  /**
   * When no engine params are provided, Nextflow will by default use the first volume claim in the
   * list provided, we need to ensure at least one such volume mount claim is sent even when not
   * explicitly mentioned, meaning that if we are matching against and empty set (no engine params
   * in the request) we need to provide at least one volume mount for Nextflow to use as the
   * default. This method returns a function that checks if the matched volume mounts is empty and
   * if so returns a list containing the first volMount that is configured for the application
   *
   * @param volMounts - the configured volume mounts in the application properties
   * @return a function that checks the matched volume list for emptiness and returns a list
   *     containing the first volMount from the volMounts parameter
   */
  private static UnaryOperator<List<String>> getDefaultVolMountIfEmptyFunctionForVolMount(
      List<String> volMounts) {
    return matchedVolMounts ->
        matchedVolMounts.isEmpty() ? List.of(volMounts.get(0)) : matchedVolMounts;
  }
}
