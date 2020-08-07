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

package org.icgc.argo.workflow_management.graphql;

import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.val;
import org.icgc.argo.workflow_management.controller.model.wes.RunsRequest;
import org.icgc.argo.workflow_management.controller.model.wes.RunsResponse;
import org.icgc.argo.workflow_management.controller.model.graphql.GqlRunsRequest;
import org.icgc.argo.workflow_management.service.WorkflowExecutionService;
import org.icgc.argo.workflow_management.service.model.WESRunParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;

@Component
public class MutationDataFetcher {

     private final WorkflowExecutionService nextflowService;

    @Autowired
    public MutationDataFetcher(@Qualifier("nextflow") WorkflowExecutionService nextflowService) {
        this.nextflowService = nextflowService;
    }

    private final MonoDataFetcher<RunsResponse> cancelRunResolver = env -> {
        val args = env.getArguments();
        String runId = String.valueOf(args.get("runId"));
        return getWorkflowService().cancel(runId);
    };

    private final MonoDataFetcher<RunsResponse> startRunResolver = env -> {
        val args = env.getArguments();

        val requestMap = ImmutableMap.<String, Object>builder();

        if (args.get("request") != null) requestMap.putAll((Map<String, Object>) args.get("request"));

        RunsRequest runsRequest = convertValue(requestMap.build(), GqlRunsRequest.class);

        val runConfig = WESRunParams.builder()
                               .workflowUrl(runsRequest.getWorkflowUrl())
                               .workflowParams(runsRequest.getWorkflowParams())
                               .workflowEngineParams(runsRequest.getWorkflowEngineParams())
                               .build();

        return getWorkflowService().run(runConfig);
    };

    public Map<String, DataFetcher> mutationResolvers() {
        return Map.of(
                "cancelRun", securityContextAddedFetcher(cancelRunResolver),
                "startRun", securityContextAddedFetcher(startRunResolver)
        );
    }


    private <T> DataFetcher<CompletableFuture<T>> securityContextAddedFetcher(MonoDataFetcher<T> monoDataFetcher) {
        return environment -> {
            // add reactive security context to mono subscriberContext so subscribers (i.e. method security) have access
            if (environment.getContext() instanceof SecurityContext) {
                SecurityContext context = environment.getContext();
                return monoDataFetcher.apply(environment)
                               .subscriberContext(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
                               .toFuture();
            }
            return monoDataFetcher.apply(environment).toFuture();
        };
    }

    private WorkflowExecutionService getWorkflowService() { return nextflowService; }

    interface MonoDataFetcher<T> extends Function<DataFetchingEnvironment, Mono<T>> {}
}
