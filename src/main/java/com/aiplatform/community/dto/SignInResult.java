package com.aiplatform.community.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignInResult {
  private final LocalDate date;
  private final boolean alreadySigned;
  private final int consecutiveDays;
}
