package com.aiplatform.community.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreationPage {
  private final List<CreationDTO> records;
  private final long total;
}
