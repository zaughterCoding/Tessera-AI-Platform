package com.aiplatform.community.service.impl;

import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.dto.CreationMediaDTO;
import com.aiplatform.community.entity.CreationMedia;
import com.aiplatform.community.enums.CreationState;
import com.aiplatform.community.mapper.CreationLikeMapper;
import com.aiplatform.community.mapper.CreationMediaMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 统一补齐媒体地址与当前用户点赞状态，避免列表逐条查询。 */
@Component
public class CreationViews {
  private final CreationMediaMapper media;
  private final CreationLikeMapper likes;

  public CreationViews(CreationMediaMapper media, CreationLikeMapper likes) {
    this.media = media;
    this.likes = likes;
  }

  public void enrich(List<CreationDTO> records, Long viewerId) {
    if (records == null || records.isEmpty()) {
      return;
    }
    List<Long> ids = records.stream().map(CreationDTO::getId).collect(Collectors.toList());
    Map<Long, List<CreationMedia>> grouped =
        media.selectByCreationIds(ids).stream()
            .collect(Collectors.groupingBy(CreationMedia::getCreationId));
    Set<Long> liked = viewerId == null ? Collections.emptySet()
        : new HashSet<>(likes.selectLikedIds(viewerId, ids));
    for (CreationDTO record : records) {
      List<CreationMedia> refs = grouped.get(record.getId());
      List<CreationMediaDTO> views = new ArrayList<>();
      if (refs != null) {
        for (CreationMedia ref : refs) {
          String url = CreationState.PUBLISHED.name().equals(record.getState())
              ? "/media/public/" + ref.getMediaId()
              : "/media/mine/" + ref.getMediaId();
          views.add(new CreationMediaDTO(ref.getMediaId(), ref.getPosition(), url));
        }
      }
      record.setMedia(views);
      record.setLiked(viewerId != null && liked.contains(record.getId()));
    }
  }

  /** 单条视图复用批量逻辑，保证详情与列表的媒体顺序一致。 */
  public CreationDTO enrich(CreationDTO record, Long viewerId) {
    if (record == null) {
      return null;
    }
    List<CreationDTO> single = new ArrayList<>();
    single.add(record);
    enrich(single, viewerId);
    return record;
  }
}
