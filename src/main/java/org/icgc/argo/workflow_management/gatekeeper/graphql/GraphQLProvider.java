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

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import com.apollographql.federation.graphqljava.Federation;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GraphQLProvider {
  public static final String RUN_QUERY_NAME = "runs";
  public static final String REMOVE_RUN_MUTATION_NAME = "removeRun";

  private final Map<String, DataFetcher> dataFetcherMap;
  private GraphQL graphQL;

  @Bean
  public GraphQL graphQL() {
    return graphQL;
  }

  @PostConstruct
  public void init() throws IOException {
    URL url = Resources.getResource("schema.graphql");
    String sdl = Resources.toString(url, Charsets.UTF_8);
    GraphQLSchema graphQLSchema = buildSchema(sdl);
    this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
  }

  private GraphQLSchema buildSchema(String sdl) {
    return Federation.transform(sdl, buildWiring()).build();
  }

  private RuntimeWiring buildWiring() {
    return RuntimeWiring.newRuntimeWiring()
        .scalar(ExtendedScalars.Json)
        .type(
            newTypeWiring("Query").dataFetcher(RUN_QUERY_NAME, dataFetcherMap.get(RUN_QUERY_NAME)))
        .type(
            newTypeWiring("Mutation")
                .dataFetcher(
                    REMOVE_RUN_MUTATION_NAME, dataFetcherMap.get(REMOVE_RUN_MUTATION_NAME)))
        .build();
  }
}
