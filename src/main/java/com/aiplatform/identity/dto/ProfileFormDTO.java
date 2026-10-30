package com.aiplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileFormDTO {
  @NotBlank
  @Size(max = 32)
  private String nickName;
}
