package org.icgc.argo.workflow_management.gatekeeper.graphql;

import static java.util.stream.Collectors.toList;
import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;

import graphql.schema.DataFetcher;
import lombok.val;
import org.icgc.argo.workflow_management.gatekeeper.model.GqlSearchQueryArgs;
import org.icgc.argo.workflow_management.gatekeeper.model.SearchResult;
import org.icgc.argo.workflow_management.gatekeeper.service.GateKeeperService;
import org.springframework.beans.factory.annotation.Qualifier;
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
  @Qualifier("activeRunsDataFetcher")
  public DataFetcher getActiveRunsDataFetcher(GateKeeperService gateKeeperService) {
    return environment -> {
      val args = convertValue(environment.getArguments(), GqlSearchQueryArgs.class);

      val page = args.getPage();
      val activeRunExample = args.getExample();
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
          activeRunExample == null
              ? gateKeeperService.getRuns(pageable)
              : gateKeeperService.getRuns(Example.of(activeRunExample), pageable);

      return new SearchResult<>(result.getContent(), result.hasNext(), result.getTotalElements());
    };
  }

  @Bean
  @Profile("!gatekeeper")
  @Qualifier("activeRunsDataFetcher")
  public DataFetcher getActiveRunsDisabledDataFetcher() {
    return environment -> null;
  }
}
