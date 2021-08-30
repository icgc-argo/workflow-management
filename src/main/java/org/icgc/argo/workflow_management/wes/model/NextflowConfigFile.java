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

package org.icgc.argo.workflow_management.wes.model;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.NonNull;
import lombok.val;

@Builder
public class NextflowConfigFile {
  private @NonNull String runName;
  private @NonNull Integer runAsUser;
  private String serviceAccount;
  private String runNamespace;
  private String imagePullPolicy;
  private String launchDir;
  private String projectDir;
  private String workDir;

  public String getConfig() throws IOException {
    val filePath = String.format("/tmp/%s.config", runName);

    File configFile = new File(filePath);
    FileWriter writer = new FileWriter(configFile);

    // Construct file contents
    List<String> fileContent = new ArrayList<>();

    fileContent.add("k8s {");

    // pod security
    fileContent.add(String.format("\trunAsUser = %s", runAsUser));

    // k8s service account (optional)
    writeFormattedLineIfValue(fileContent::add, "\tnamespace= '%s'", runNamespace);
    writeFormattedLineIfValue(fileContent::add, "\tserviceAccount = '%s'", serviceAccount);

    // k8s image pull policy for run
    fileContent.add(String.format("\tpullPolicy = '%s'", imagePullPolicy));

    // variable config passed in via WorkflowEngineParams
    writeFormattedLineIfValue(fileContent::add, "\tlaunchDir = '%s'", launchDir);
    writeFormattedLineIfValue(fileContent::add, "\tprojectDir = '%s'", projectDir);
    writeFormattedLineIfValue(fileContent::add, "\tworkDir = '%s'", workDir);

    // set plugins mode to prod
    fileContent.add("\tpod = [env: 'NXF_PLUGINS_MODE', value: 'prod']");

    // close k8s off
    fileContent.add("}");

    // Write contents to file
    for (String line : fileContent) {
      writer.write(line + System.lineSeparator());
    }

    // write newline at end of file and close
    writer.write(String.format("%n"));
    writer.flush();
    writer.close();

    // Return path for usage in nextflow
    return filePath;
  }

  private static void writeFormattedLineIfValue(
      @NonNull Consumer<String> consumer, @NonNull String formatted, String value) {
    if (!isNullOrEmpty(value)) {
      consumer.accept(String.format(formatted, value));
    }
  }
}
