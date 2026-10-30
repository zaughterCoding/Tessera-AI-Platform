package com.aiplatform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CreationUpdateForm {
  @NotBlank
  @Size(max = 128)
  private String title;

  @NotBlank
  @Size(max = 20000)
  private String content;

  @NotNull
  @Size(max = 9)
  private List<@NotNull @Positive Long> mediaIds;
}
