package com.aiplatform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CreationForm {
  @NotNull
  @Positive
  private Long applicationId;

  @NotBlank
  @Pattern(regexp = "WORK|REVIEW")
  private String kind;

  @NotBlank
  @Size(max = 128)
  private String title;

  @NotBlank
  @Size(max = 20000)
  private String content;

  /** 明确选择公开可见的素材；未选中的上传仍保持私有。 */
  @NotNull
  @Size(max = 9)
  private List<@NotNull @Positive Long> mediaIds;

  @NotNull
  private Boolean published;
}
