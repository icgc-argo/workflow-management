package org.icgc.argo.workflow_management.util;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.icgc.argo.workflow_management.wes.model.WorkflowEngineParams;
import org.icgc.argo.workflow_management.wes.properties.NextflowProperties;

public class VolumeMounts {
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
                        .collect(Collectors.toList())))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "At least one volMount must be configured in order for Nextflow to run in Kubernetes"));
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
        .filter(
            volMount ->
                paths.stream()
                    .anyMatch(getPathMatchFunctionForVolMount(volMount, volMounts.get(0))))
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
   * @param defaultVolMount in the event that the path is null (not set by the user) we want to
   *     still match on the default provided as Nextflow will still schedule in that directory
   * @return a function which evaluates that the path provided matches the pattern for the volMount
   *     used to generate this function
   */
  private static Predicate<String> getPathMatchFunctionForVolMount(
      String volMount, String defaultVolMount) {
    return path ->
        Optional.ofNullable(path)
            .map(definedPath -> definedPath.startsWith(volMount.split(":")[1]))
            .orElseGet(() -> volMount.equals(defaultVolMount));
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
