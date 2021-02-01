/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
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

package org.icgc.argo.workflow_management.service.wes.model;

import static org.icgc.argo.workflow_management.util.Reflections.invokeDeclaredMethod;

import io.fabric8.kubernetes.api.model.Pod;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nextflow.NextflowMeta;
import nextflow.cli.CmdKubeRun;
import nextflow.config.Manifest;
import nextflow.k8s.K8sDriverLauncher;
import nextflow.trace.WorkflowStats;
import nextflow.util.Duration;
import org.icgc.argo.workflow_management.exception.ReflectionUtilsException;

/** Side effect-free data object mimic-ing nextflow's WorkflowMetadata class... */
@Slf4j
@Data
@NoArgsConstructor
public class NextflowWorkflowMetadata {
  private String runName;
  private String scriptId;
  private Path scriptFile;
  private String scriptName;
  private String repository;
  private String commitId;
  private String revision;
  private OffsetDateTime start;
  private OffsetDateTime complete;
  private Duration duration;
  private Object container;
  private String commandLine;
  private NextflowMeta nextflow;
  private boolean success;
  private Path projectDir;
  private String projectName;
  private Path launchDir;
  private Path workDir;
  private Path homeDir;
  private String userName;
  private Integer exitStatus;
  private String errorMessage;
  private String errorReport;
  private String profile;
  private UUID sessionId;
  private boolean resume;
  private String containerEngine;
  private List<Path> configFiles;
  private WorkflowStats stats;
  private Manifest manifest;

  public void update(Pod pod) {
    this.setContainer(pod.getSpec().getContainers());
    this.setContainerEngine("Docker?");
    this.setExitStatus(0);
    this.setStart(OffsetDateTime.parse(pod.getStatus().getStartTime()));
  }

  public static NextflowWorkflowMetadata create(CmdKubeRun cmd, K8sDriverLauncher driver) {
    NextflowWorkflowMetadata metadata = new NextflowWorkflowMetadata();
    String commandLine;
    try {
      commandLine = invokeDeclaredMethod(driver, "getLaunchCli", String.class);
    } catch (ReflectionUtilsException e) {
      log.error(
          "Caught ReflectionUtilsException while trying to invoke method 'getLaunchCli':"
              + e.toString());
      commandLine = "?";
    }
    metadata.setCommandLine(commandLine);
    metadata.setProfile(cmd.getProfile());
    metadata.setRevision(cmd.getRevision());
    metadata.setRunName(cmd.getRunName());
    metadata.setSuccess(false);
    return metadata;
  }
}
