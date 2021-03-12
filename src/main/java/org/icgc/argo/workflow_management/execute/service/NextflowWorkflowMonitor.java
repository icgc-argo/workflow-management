/*
 * Copyright (c) 2021 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.argo.workflow_management.execute.service;

import static java.lang.String.format;
import static java.time.OffsetDateTime.now;
import static org.icgc.argo.workflow_management.execute.service.NextflowService.NEXTFLOW_PREFIX;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.execute.model.KubernetesPhase;
import org.icgc.argo.workflow_management.execute.model.NextflowEvent;
import org.icgc.argo.workflow_management.execute.model.NextflowMetadata;
import org.icgc.argo.workflow_management.stream.components.WebLogEventSender;

@Slf4j
@AllArgsConstructor
public class NextflowWorkflowMonitor implements Runnable {

  private final WebLogEventSender webLogSender;
  private final NextflowMetadata metadata;
  private final Integer maxErrorLogLines;
  private final DefaultKubernetesClient kubernetesClient;

  public void run() {
    boolean done = false;
    val podName = metadata.getWorkflow().getRunName();
    while (!done) {
      try {
        PodResource<Pod, DoneablePod> pod = kubernetesClient.pods().withName(podName);
        val p = pod.get();
        val log = pod.tailingLines(maxErrorLogLines).getLog();
        done = handlePod(metadata, p, log);
      } catch (Exception e) {
        log.error(format("Workflow Status Monitor threw exception %s", e.getMessage()));
      }
    }
  }

  public boolean handlePod(NextflowMetadata metadata, Pod pod, String podLog) {
    val podName = pod.getMetadata().getName();
    // if the pod running nextflow has created children, we'll assume it started successfully, and
    // that it can handle it's own logging from here on in.
    if (podHasChildren(podName) || podSucceeded(pod)) {
      // state and not attempt to emit events until that is in an error state
      log.debug(podName + " has children! Done!");
      return true;
    }

    // if the pod failed to start up, we'll log the start and end events, so that we know
    // that the pod has started, and has failed, with the pod log as the error report.
    if (podFailed(pod)) {
      val workflow = metadata.getWorkflow();

      workflow.update(pod);
      workflow.setComplete(now(ZoneOffset.UTC));
      workflow.setErrorReport("Nextflow pod failed to start: " + podLog);
      workflow.setSuccess(false);

      log.debug("Sending error nextflow event");
      webLogSender.sendNextflowEventAsync(metadata, NextflowEvent.ERROR);
      return true;
    }

    // otherwise, we need to check in again in a moment or two
    return false;
  }

  private boolean podHasChildren(String podName) {
    val childPods =
        kubernetesClient.pods().withLabel("runName", podName).list().getItems().stream()
            .filter(pod -> pod.getMetadata().getName().startsWith(NEXTFLOW_PREFIX))
            .collect(Collectors.toList());

    return childPods.size() > 0;
  }

  private boolean podSucceeded(Pod pod) {
    return getPhase(pod).equals(KubernetesPhase.SUCCEEDED);
  }

  private boolean podFailed(Pod pod) {
    return getPhase(pod).equals(KubernetesPhase.FAILED);
  }

  public KubernetesPhase getPhase(Pod pod) {
    return KubernetesPhase.valueOf(pod.getStatus().getPhase().toUpperCase());
  }
}
