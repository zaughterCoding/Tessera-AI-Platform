package com.aiplatform.community.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreationMediaDTO {
  private Long mediaId;
  private Integer position;
  private String url;
}
