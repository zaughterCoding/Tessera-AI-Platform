package com.aiplatform.community.service.impl;

import com.aiplatform.catalog.mapper.AiApplicationMapper;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.dto.CreationForm;
import com.aiplatform.community.dto.CreationPage;
import com.aiplatform.community.dto.CreationUpdateForm;
import com.aiplatform.community.dto.LikeResult;
import com.aiplatform.community.entity.Creation;
import com.aiplatform.community.enums.CreationKind;
import com.aiplatform.community.enums.CreationState;
import com.aiplatform.community.mapper.CreationLikeMapper;
import com.aiplatform.community.mapper.CreationMapper;
import com.aiplatform.community.mapper.CreationMediaMapper;
import com.aiplatform.community.service.ICreationService;
import com.aiplatform.media.MediaConfig;
import com.aiplatform.media.service.IMediaService;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 发布必须以本账号可用素材为引用；私有作品只在本人端点可见。 */
@Service
public class CreationServiceImpl implements ICreationService {
  private static final int STATUS_PUBLISHED = 1;
  private static final int MAX_PAGE = 50;

  private final CreationMapper creations;
  private final CreationMediaMapper references;
  private final CreationLikeMapper likes;
  private final AiApplicationMapper applications;
  private final IMediaService media;
  private final MediaConfig mediaConfig;
  private final IBusinessOutboxService outbox;
  private final CreationViews views;

  public CreationServiceImpl(
      CreationMapper creations,
      CreationMediaMapper references,
      CreationLikeMapper likes,
      AiApplicationMapper applications,
      IMediaService media,
      MediaConfig mediaConfig,
      IBusinessOutboxService outbox,
      CreationViews views) {
    this.creations = creations;
    this.references = references;
    this.likes = likes;
    this.applications = applications;
    this.media = media;
    this.mediaConfig = mediaConfig;
    this.outbox = outbox;
    this.views = views;
  }

  @Override
  @Transactional
  public CreationDTO create(long userId, CreationForm form) {
    requirePublishedApplication(form.getApplicationId());
    List<Long> selected = mediaIds(form.getMediaIds());
    media.lockOwnedAssets(userId, selected);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    boolean published = Boolean.TRUE.equals(form.getPublished());
    Creation creation = new Creation();
    creation.setUserId(userId);
    creation.setApplicationId(form.getApplicationId());
    creation.setKind(form.getKind());
    creation.setTitle(form.getTitle().trim());
    creation.setContent(form.getContent().trim());
    creation.setState(published ? CreationState.PUBLISHED.name() : CreationState.PRIVATE.name());
    creation.setPublishedAt(published ? now : null);
    creations.insert(creation);
    insertReferences(creation.getId(), selected);
    if (published) {
      outbox.appendEvent(BusinessEventType.CREATION_PUBLISH.name(), creation.getId(), userId);
    }
    return ownedView(userId, creation.getId());
  }

  @Override
  @Transactional
  public CreationDTO update(long userId, long creationId, CreationUpdateForm form) {
    requireOwnedForUpdate(userId, creationId);
    List<Long> selected = mediaIds(form.getMediaIds());
    media.lockOwnedAssets(userId, selected);
    if (creations.updateContent(creationId, userId, form.getTitle().trim(),
        form.getContent().trim()) != 1) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    // 编辑发布时重新声明公开素材；被移除的素材若未被其他发布引用则回到私有状态。
    references.deleteByCreation(creationId);
    insertReferences(creationId, selected);
    return ownedView(userId, creationId);
  }

  @Override
  @Transactional
  public CreationDTO setVisibility(long userId, long creationId, boolean published) {
    Creation creation = requireOwnedForUpdate(userId, creationId);
    boolean currently = CreationState.PUBLISHED.name().equals(creation.getState());
    if (currently == published) {
      return ownedView(userId, creationId);
    }
    String target = published ? CreationState.PUBLISHED.name() : CreationState.PRIVATE.name();
    if (creations.updateVisibility(creationId, userId, target,
        published ? LocalDateTime.now(ZoneOffset.UTC) : null) != 1) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (published) {
      outbox.appendEvent(BusinessEventType.CREATION_PUBLISH.name(), creationId, userId);
    }
    return ownedView(userId, creationId);
  }

  @Override
  @Transactional
  public void delete(long userId, long creationId) {
    requireOwnedForUpdate(userId, creationId);
    if (creations.deleteOwned(creationId, userId) != 1) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    // 引用随作品级联删除；素材本身仍归用户所有，是否删除由素材端点明确决定。
  }

  @Override
  public CreationDTO publicDetail(Long viewerId, long creationId) {
    CreationDTO view = creations.selectViewById(creationId);
    if (view == null || !CreationState.PUBLISHED.name().equals(view.getState())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return views.enrich(view, viewerId);
  }

  @Override
  public CreationDTO ownedDetail(long userId, long creationId) {
    return ownedView(userId, creationId);
  }

  @Override
  public CreationPage publicPage(Long viewerId, int limit, int offset) {
    return page(creations.selectPublic(safeLimit(limit), safeOffset(offset)),
        creations.countPublic(), viewerId);
  }

  @Override
  public CreationPage hotPage(Long viewerId, int limit, int offset) {
    return page(creations.selectHot(safeLimit(limit), safeOffset(offset)),
        creations.countPublic(), viewerId);
  }

  @Override
  public CreationPage applicationPage(Long viewerId, long applicationId, int limit, int offset) {
    int size = safeLimit(limit);
    int start = safeOffset(offset);
    return page(creations.selectPublishedByApplication(applicationId, size, start),
        creations.countPublishedByApplication(applicationId), viewerId);
  }

  @Override
  public CreationPage authorPage(Long viewerId, long authorId, int limit, int offset) {
    int size = safeLimit(limit);
    int start = safeOffset(offset);
    return page(creations.selectPublishedByAuthor(authorId, size, start),
        creations.countPublishedByAuthor(authorId), viewerId);
  }

  @Override
  public CreationPage mine(long userId, int limit, int offset) {
    int size = safeLimit(limit);
    int start = safeOffset(offset);
    return page(creations.selectMine(userId, size, start),
        creations.countMine(userId), userId);
  }

  @Override
  @Transactional
  public LikeResult setLiked(long userId, long creationId, boolean liked) {
    Creation creation = creations.selectByIdForUpdate(creationId);
    if (creation == null || !CreationState.PUBLISHED.name().equals(creation.getState())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (liked) {
      if (likes.insertIgnore(creationId, userId) == 1) {
        creations.incrementLike(creationId);
      }
    } else if (likes.delete(creationId, userId) == 1) {
      creations.decrementLike(creationId);
    }
    Creation live = creations.selectById(creationId);
    int count = live == null || live.getLikeCount() == null ? 0 : live.getLikeCount();
    return new LikeResult(creationId, liked, count);
  }

  private CreationPage page(List<CreationDTO> records, long total, Long viewerId) {
    views.enrich(records, viewerId);
    return new CreationPage(records, total);
  }

  private CreationDTO ownedView(long userId, long creationId) {
    CreationDTO view = creations.selectViewById(creationId);
    if (view == null || !Long.valueOf(userId).equals(view.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return views.enrich(view, userId);
  }

  private Creation requireOwnedForUpdate(long userId, long creationId) {
    Creation creation = creations.selectByIdForUpdate(creationId);
    if (creation == null || !Long.valueOf(userId).equals(creation.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (!CreationKind.WORK.name().equals(creation.getKind())
        && !CreationKind.REVIEW.name().equals(creation.getKind())) {
      throw new IllegalStateException("作品类型不合法");
    }
    return creation;
  }

  private List<Long> mediaIds(List<Long> mediaIds) {
    if (mediaIds == null || mediaIds.isEmpty()) {
      return Collections.emptyList();
    }
    if (mediaIds.size() > mediaConfig.maxCreationMedia()) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    return mediaIds;
  }

  private void insertReferences(long creationId, List<Long> mediaIds) {
    for (int index = 0; index < mediaIds.size(); index++) {
      references.insert(creationId, mediaIds.get(index), index);
    }
  }

  private void requirePublishedApplication(long applicationId) {
    Integer status = applications.selectStatus(applicationId);
    if (!Integer.valueOf(STATUS_PUBLISHED).equals(status)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
  }

  private int safeLimit(int limit) {
    return Math.max(1, Math.min(limit, MAX_PAGE));
  }

  private int safeOffset(int offset) {
    return Math.max(0, Math.min(offset, 100000));
  }
}
