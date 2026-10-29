package com.aiplatform.community.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
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
