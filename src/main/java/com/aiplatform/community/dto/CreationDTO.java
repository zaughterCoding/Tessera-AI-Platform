package com.aiplatform.community.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CreationDTO {
  private Long id;
  private Long userId;
  private String authorNickName;
  private String authorIcon;
  private Long applicationId;
  private String applicationName;
  private String kind;
  private String title;
  private String content;
  private String state;
  private Integer likeCount;
  private Boolean liked;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private List<CreationMediaDTO> media = new ArrayList<>();
}
