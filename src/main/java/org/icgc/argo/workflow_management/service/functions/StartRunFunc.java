package org.icgc.argo.workflow_management.service.functions;

import java.util.function.Function;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface StartRunFunc extends Function<RunParams, Mono<RunsResponse>> {}
