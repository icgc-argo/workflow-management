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

package org.icgc.argo.workflow_management.secret.impl;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.secret.SecretProvider;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyProvider extends SecretProvider {

  /** Dependencies */
  private final Boolean enabled;

  private final String apiKey;

  @Override
  public Optional<String> generateSecret() {
    log.debug("ApiKeyProvider returning secret.");
    return enabled ? Optional.of(apiKey) : Optional.empty();
  }

  /**
   * API Keys do not have the ability to have their scopes modified as they are already issued at
   * call time.
   *
   * @return API Key
   */
  @Override
  public Optional<String> generateSecretWithScopes(List<String> scopes) {
    log.debug("ApiKeyProvider returning secret.");
    log.warn("Trying to generate API key that is scoped. API Keys cannot be dynamically scoped!");
    return generateSecret();
  }

  @Override
  public Boolean isEnabled() {
    return enabled;
  }
}
