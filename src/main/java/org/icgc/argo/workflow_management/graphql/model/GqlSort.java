package org.icgc.argo.workflow_management.graphql.model;

import lombok.Data;

@Data
public class GqlSort {
  String fieldName;
  String order;

  //  enum Order {
  //    asc,
  //    desc
  //  }
}
