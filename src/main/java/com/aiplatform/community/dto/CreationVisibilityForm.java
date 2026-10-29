package com.aiplatform.community.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreationVisibilityForm {
  @NotNull
  private Boolean published;
}
