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

package org.icgc.argo.workflow_management.secret.impl;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.secret.SecretProvider;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;

@Slf4j
@RequiredArgsConstructor
public class OAuth2BearerTokenProvider extends SecretProvider {

  /** Dependencies */
  private final Boolean enabled;

  private final String clientId;
  private final String clientSecret;
  private final String tokenUri;

  @Override
  public Optional<String> generateSecret() {
    log.debug("OAuth2BearerTokenProvider generating secret with default scopes");
    return enabled ? Optional.of(getRestTemplate().getAccessToken().getValue()) : Optional.empty();
  }

  @Override
  public Optional<String> generateSecretWithScopes(@NonNull List<String> scopes) {
    log.debug("OAuth2BearerTokenProvider generating secret with scopes: {}", scopes);
    return enabled
        ? Optional.of(getRestTemplate(scopes).getAccessToken().getValue())
        : Optional.empty();
  }

  @Override
  public Boolean isEnabled() {
    return enabled;
  }

  private OAuth2RestTemplate getRestTemplate() {
    return getRestTemplate(List.of());
  }

  private OAuth2RestTemplate getRestTemplate(@NonNull List<String> scopes) {
    log.debug(
        "Requesting new Bearer Token with client credentials grant using clientId: {}", clientId);
    val clientCredentialDetails = new ClientCredentialsResourceDetails();
    clientCredentialDetails.setClientId(clientId);
    clientCredentialDetails.setClientSecret(clientSecret);
    clientCredentialDetails.setAccessTokenUri(tokenUri);
    if (!scopes.isEmpty()) {
      clientCredentialDetails.setScope(scopes);
    }
    return new OAuth2RestTemplate(clientCredentialDetails);
  }
}
