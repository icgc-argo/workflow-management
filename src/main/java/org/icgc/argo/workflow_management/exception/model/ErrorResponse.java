package org.icgc.argo.workflow_management.exception.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.*;

/** An object that can optionally include information about the error. */
@ApiModel(description = "Standard error response")
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ErrorResponse {

  @JsonProperty("msg")
  private String msg;

  @JsonProperty("status_code")
  private Integer statusCode;
}
