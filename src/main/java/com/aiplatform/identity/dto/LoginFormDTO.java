package com.aiplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginFormDTO {
  @NotBlank
  @Pattern(regexp = "1[3-9][0-9]{9}")
  private String phone;

  @NotBlank
  @Pattern(regexp = "[0-9]{6}")
  private String code;
}
