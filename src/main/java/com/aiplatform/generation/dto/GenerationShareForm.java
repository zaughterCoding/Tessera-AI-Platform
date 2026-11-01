package com.aiplatform.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 用户显式选择已交付结果并确认发布；默认私有，未选中的结果不会进入作品。 */
@Data
public class GenerationShareForm {
  @NotNull
  @Size(min = 1, max = 9)
  private List<@NotNull @Positive Long> resultIds;

  @NotBlank
  @Size(max = 128)
  private String title;

  @NotBlank
  @Size(max = 20000)
  private String content;

  /** 缺省为 false：先保存为私有作品，公开必须由用户再次明确选择。 */
  private Boolean published;
}
