package org.icgc.argo.workflow_management.util;

import static java.lang.String.format;
import static org.icgc.argo.workflow_management.service.model.Constants.WES_PREFIX;

import java.util.UUID;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WesUtils {
  // run name (used for paramsFile as well)
  // You may be asking yourself, why is he replacing the "-" in the UUID, this is a valid
  // question, well unfortunately when trying to resume a job, Nextflow searches for the
  // UUID format ANYWHERE in the resume string, resulting in the incorrect assumption
  // that we are passing an runId when in fact we are passing a runName ...
  // thanks Nextflow ... this workaround solves that problem

  // UPDATE: The glory of Nextflow knows no bounds ... resuming by runName while possible
  // ends up reusing the run/session (yeah these are the same but still recorded separately) id
  // from the "last" run ... wtv run that was ... resulting in multiple resumed runs sharing the
  // same sessionId (we're going with this label) even though they have nothing to do with one
  // another. This is a bug in NF and warrants a PR but for now we recommend only resuming runs
  // with sessionId and never with runName
  public static String generateWesRunName() {
    return format("%s%s", WES_PREFIX, UUID.randomUUID().toString().replace("-", ""));
  }

  public static Boolean isValidWesRunName(String runName) {
    return runName != null && runName.startsWith(WES_PREFIX) && runName.split("-").length == 2;
  }
}
