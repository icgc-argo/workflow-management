package org.icgc.argo.workflow_management.exception.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ApiModel(description = "Standard error response")
public class ErrorResponse {

  @JsonProperty("msg")
  private String msg;

  @JsonProperty("status_code")
  private Integer statusCode;
}
