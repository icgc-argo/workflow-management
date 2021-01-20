package org.icgc.argo.workflow_management.service.functions;

import java.util.function.Function;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

public interface CancelRunFunc extends Function<String, Mono<RunsResponse>> {}
