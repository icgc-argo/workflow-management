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

package org.icgc.argo.workflow_management.gatekeeper.graphql;

import static java.util.stream.Collectors.toList;
import static org.icgc.argo.workflow_management.gatekeeper.graphql.GraphQLProvider.REMOVE_RUN_MUTATION_NAME;
import static org.icgc.argo.workflow_management.gatekeeper.graphql.GraphQLProvider.RUN_QUERY_NAME;
import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import java.util.Map;
import lombok.val;
import org.icgc.argo.workflow_management.gatekeeper.graphql.model.GqlSearchQueryArgs;
import org.icgc.argo.workflow_management.gatekeeper.graphql.model.SearchResult;
import org.icgc.argo.workflow_management.gatekeeper.service.GateKeeperService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class GatekeeperDataFetchers {

  @Bean
  @Profile("gatekeeper")
  public Map<String, DataFetcher> dataFetchersMap(GateKeeperService gateKeeperService) {
    return ImmutableMap.of(
        RUN_QUERY_NAME,
        getActiveRunsDataFetcher(gateKeeperService),
        REMOVE_RUN_MUTATION_NAME,
        getRemoveRunDataFetcher(gateKeeperService));
  }

  @Bean
  @Profile("!gatekeeper")
  public Map<String, DataFetcher> disabledDataFetchersMap() {
    final DataFetcher NO_OP_FETCHER = environment -> null;
    return ImmutableMap.of(RUN_QUERY_NAME, NO_OP_FETCHER, REMOVE_RUN_MUTATION_NAME, NO_OP_FETCHER);
  }

  private DataFetcher getActiveRunsDataFetcher(GateKeeperService gateKeeperService) {
    return environment -> {
      val args = convertValue(environment.getArguments(), GqlSearchQueryArgs.class);

      val page = args.getPage();
      val runExample = args.getExample();
      val sorts = args.getSorts();

      val sortable =
          sorts == null
              ? Sort.unsorted()
              : Sort.by(
                  sorts.stream()
                      .map(
                          s ->
                              new Sort.Order(
                                  s.getOrder().equalsIgnoreCase("asc")
                                      ? Sort.Direction.ASC
                                      : Sort.Direction.DESC,
                                  s.getFieldName()))
                      .collect(toList()));

      val pageable =
          page == null
              ? PageRequest.of(0, 10, sortable)
              : PageRequest.of(page.getFrom(), page.getSize(), sortable);

      val result =
          runExample == null
              ? gateKeeperService.getRuns(pageable)
              : gateKeeperService.getRuns(Example.of(runExample), pageable);

      return new SearchResult<>(result.getContent(), result.hasNext(), result.getTotalElements());
    };
  }

  private DataFetcher getRemoveRunDataFetcher(GateKeeperService gateKeeperService) {
    return environment -> gateKeeperService.removeRun(environment.getArgument("runId"));
  }
}
