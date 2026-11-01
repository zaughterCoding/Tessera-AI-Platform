package com.aiplatform.generation.service;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.dto.CreationForm;
import com.aiplatform.community.service.ICreationService;
import com.aiplatform.generation.GenerationResultState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.dto.GenerationShareForm;
import com.aiplatform.generation.entity.GenerationResult;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.mapper.GenerationResultMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.media.service.IMediaService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结果分享复用既有作品与媒体能力：只接受本人已交付结果显式选中的位置，
 * 素材归属、可用状态与应用可发布性仍由社区/媒体服务校验，默认保存为私有作品。
 */
@Service
public class GenerationShareService {
  private final GenerationTaskMapper tasks;
  private final GenerationResultMapper results;
  private final IMediaService media;
  private final ICreationService creations;

  public GenerationShareService(GenerationTaskMapper tasks, GenerationResultMapper results,
      IMediaService media, ICreationService creations) {
    this.tasks = tasks;
    this.results = results;
    this.media = media;
    this.creations = creations;
  }

  @Transactional
  public CreationDTO share(long userId, long taskId, GenerationShareForm form) {
    GenerationTask task = tasks.selectById(taskId);
    if (task == null || !Long.valueOf(userId).equals(task.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (!GenerationTaskState.SUCCEEDED.equals(task.getState())) {
      // 只有全部结果已交付并结算的任务才能分享；未完成任务的结果不对外暴露。
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    List<Long> mediaIds = selectedMedia(taskId, form.getResultIds());
    media.lockOwnedAssets(userId, mediaIds);
    CreationForm creation = new CreationForm();
    creation.setApplicationId(task.getApplicationId());
    creation.setKind("WORK");
    creation.setTitle(form.getTitle().trim());
    creation.setContent(form.getContent().trim());
    creation.setMediaIds(mediaIds);
    creation.setPublished(Boolean.TRUE.equals(form.getPublished()));
    return creations.create(userId, creation);
  }

  /** 选中结果必须属于本任务且已交付；任一位置不可用即拒绝整次分享。 */
  private List<Long> selectedMedia(long taskId, List<Long> resultIds) {
    Set<Long> requested = new HashSet<>(resultIds);
    if (requested.size() != resultIds.size()) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    List<GenerationResult> owned = results.selectByTaskId(taskId);
    List<Long> mediaIds = new ArrayList<>();
    for (GenerationResult result : owned) {
      if (!requested.contains(result.getId())) {
        continue;
      }
      if (!GenerationResultState.DELIVERED.equals(result.getState())
          || result.getMediaId() == null) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      mediaIds.add(result.getMediaId());
    }
    if (mediaIds.size() != requested.size()) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return mediaIds;
  }
}
