package com.aiplatform.community.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
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
