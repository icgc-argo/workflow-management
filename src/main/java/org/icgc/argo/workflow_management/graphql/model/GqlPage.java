package org.icgc.argo.workflow_management.graphql.model;

import lombok.Data;

@Data
public class GqlPage {
  Integer from;
  Integer size;
}
