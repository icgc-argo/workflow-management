package org.icgc.argo.workflow_management.graphql;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.icgc.argo.workflow_management.util.JacksonUtils.convertValue;

import com.google.common.collect.ImmutableList;
import graphql.schema.DataFetcher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.icgc.argo.workflow_management.gatekeeper.model.ActiveRun;
import org.icgc.argo.workflow_management.gatekeeper.service.GateKeeperService;
import org.icgc.argo.workflow_management.graphql.model.GqlPage;
import org.icgc.argo.workflow_management.graphql.model.GqlSort;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Profile("gatekeeper")
@Component
@RequiredArgsConstructor
public class QueryDataFetchers {

  private final GateKeeperService gateKeeperService;

  public DataFetcher getActiveRunsDataFetcher() {
    return environment -> {
      val args = environment.getArguments();

      val sortsBuilder = ImmutableList.<GqlSort>builder();
      GqlPage page = null;
      ActiveRun activeRun = null;
      if (args != null) {
        if (args.get("example") != null)
          activeRun = convertValue(args.get("example"), ActiveRun.class);
        if (args.get("page") != null) page = convertValue(args.get("page"), GqlPage.class);
        if (args.get("sorts") != null) {
          val rawSorts = (List<Object>) args.get("sorts");
          sortsBuilder.addAll(
              rawSorts.stream()
                  .map(sort -> convertValue(sort, GqlSort.class))
                  .collect(toUnmodifiableList()));
        }
      }

      val sorts = sortsBuilder.build();

      val sortable =
          Sort.by(
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

      if (activeRun == null) {
        return gateKeeperService.getRuns(pageable);
      } else {
        val example = Example.of(activeRun);
        return gateKeeperService.getRuns(example, pageable);
      }
    };
  }
}
