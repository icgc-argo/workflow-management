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

package org.icgc.argo.workflow_management.wes.properties;

import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "nextflow")
public class NextflowProperties {
  private K8sProperties k8s;
  private MonitorProperties monitor;
  private String weblogUrl;
  private String masterUrl;
  private boolean trustCertificate;
  private Map<String, ClusterProperties> cluster;

  @Data
  public static class K8sProperties {
    private Integer runAsUser;
    private String serviceAccount;
    private String namespace;
    private String runNamespace;
    private String imagePullPolicy;
    private String pluginsDir;
    private List<String> volMounts;
    private String masterUrl;
    private String context;
    private boolean trustCertificate;
  }

  @Data
  public static class MonitorProperties {
    private Integer sleepInterval;
    private Integer maxErrorLogLines;
  }

  @Data
  public static class ClusterProperties {
    /*private HashMap<String, String> context;
    private HashMap<String, String> masterUrl;
    private HashMap<String, List<String>> volMounts;*/

    private String context;
    private String masterUrl;
    private List<String> volMounts;
  }
}
