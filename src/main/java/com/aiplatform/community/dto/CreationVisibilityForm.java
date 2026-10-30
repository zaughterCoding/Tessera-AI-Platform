package com.aiplatform.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreationVisibilityForm {
  @NotNull
  private Boolean published;
}
