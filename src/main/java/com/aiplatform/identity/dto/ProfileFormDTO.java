package com.aiplatform.identity.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileFormDTO {
  @NotBlank
  @Size(max = 32)
  private String nickName;
}
