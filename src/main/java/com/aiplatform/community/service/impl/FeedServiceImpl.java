package com.aiplatform.community.service.impl;

import com.aiplatform.community.CommunityConfig;
import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.dto.FeedResult;
import com.aiplatform.community.entity.Creation;
import com.aiplatform.community.entity.FollowEdge;
import com.aiplatform.community.enums.CreationState;
import com.aiplatform.community.mapper.CreationMapper;
import com.aiplatform.community.mapper.FollowMapper;
import com.aiplatform.community.service.IFeedService;
import com.aiplatform.messaging.ProjectionResult;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Feed 使用发布时间为分值、作品 id 为成员的 ZSet；游标是时间加同分偏移，
 * 粉丝写入按自增边 id 分批，重复批次只会重复写同一成员。
 */
@Service
public class FeedServiceImpl implements IFeedService {
  private final CreationMapper creations;
  private final FollowMapper follows;
  private final StringRedisTemplate redis;
  private final CreationViews views;
  private final CommunityConfig config;
  private final String prefix;

  public FeedServiceImpl(
      CreationMapper creations,
      FollowMapper follows,
      StringRedisTemplate redis,
      CreationViews views,
      CommunityConfig config,
      @Value("${app.redis.key-prefix}") String prefix) {
    this.creations = creations;
    this.follows = follows;
    this.redis = redis;
    this.views = views;
    this.config = config;
    this.prefix = prefix;
  }

  @Override
  public ProjectionResult fanout(long creationId, long authorId, long afterFollowId) {
    Creation creation = creations.selectById(creationId);
    if (creation == null || !Objects.equals(creation.getUserId(), authorId)) {
      return ProjectionResult.complete();
    }
    if (!CreationState.PUBLISHED.name().equals(creation.getState())) {
      return ProjectionResult.complete();
    }
    LocalDateTime publishedAt = creation.getPublishedAt() == null
        ? creation.getCreatedAt() : creation.getPublishedAt();
    long score = publishedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    if (afterFollowId == 0) {
      // 作者自己的 Feed 也包含自己的发布；同一成员重复写入不会产生重复项。
      write(authorId, creationId, score);
    }
    int batch = config.feedBatch();
    List<FollowEdge> rows = follows.selectFollowerBatch(authorId, afterFollowId, batch + 1);
    int processed = Math.min(rows.size(), batch);
    for (int index = 0; index < processed; index++) {
      long followerId = rows.get(index).getUserId();
      if (followerId != authorId) {
        write(followerId, creationId, score);
      }
    }
    if (rows.size() > batch) {
      return ProjectionResult.continueAt(rows.get(batch - 1).getId());
    }
    return ProjectionResult.complete();
  }

  private void write(long userId, long creationId, long score) {
    Boolean added = redis.opsForZSet().add(key(userId), String.valueOf(creationId),
        (double) score);
    if (added == null) {
      throw new IllegalStateException("Feed 写入未完成");
    }
  }

  @Override
  public FeedResult feed(long userId, Long lastTime, int offset, int size) {
    int page = Math.max(1, Math.min(size, config.feedPageMax()));
    int start = Math.max(0, Math.min(offset, 2000));
    String key = key(userId);
    long cursorTime = lastTime == null || lastTime <= 0 ? Long.MAX_VALUE : lastTime;
    int cursorOffset = start;
    List<CreationDTO> records = new ArrayList<>();
    boolean exhausted = false;
    int rounds = 0;
    while (records.size() < page && rounds++ < 20 && !exhausted) {
      int needed = page - records.size();
      Set<TypedTuple<String>> tuples = redis.opsForZSet()
          .reverseRangeByScoreWithScores(key, 0, cursorTime, cursorOffset, needed);
      if (tuples == null) {
        throw new IllegalStateException("Feed 读取失败");
      }
      if (tuples.isEmpty()) {
        exhausted = true;
        break;
      }
      List<TypedTuple<String>> ordered = new ArrayList<>(tuples);
      long lowest = ordered.get(ordered.size() - 1).getScore().longValue();
      int sameScore = 0;
      for (TypedTuple<String> tuple : ordered) {
        if (tuple.getScore() != null && tuple.getScore().longValue() == lowest) {
          sameScore++;
        }
      }
      long previousTime = cursorTime;
      int previousOffset = cursorOffset;
      cursorTime = lowest;
      cursorOffset = lowest == previousTime ? previousOffset + sameScore : sameScore;
      if (cursorTime == previousTime && cursorOffset == previousOffset) {
        break;
      }
      if (ordered.size() < needed) {
        exhausted = true;
      }
      List<Long> ids = ordered.stream()
          .map(TypedTuple::getValue)
          .filter(Objects::nonNull)
          .map(FeedServiceImpl::parseId)
          .filter(id -> id != null)
          .collect(Collectors.toList());
      if (!ids.isEmpty()) {
        Map<Long, CreationDTO> found = creations.selectPublishedByIds(ids).stream()
            .collect(Collectors.toMap(CreationDTO::getId, Function.identity(), (a, b) -> a));
        for (Long id : ids) {
          CreationDTO view = found.get(id);
          if (view != null) {
            records.add(view);
          }
        }
      }
    }
    boolean hasMore = false;
    if (!exhausted) {
      Set<TypedTuple<String>> probe = redis.opsForZSet()
          .reverseRangeByScoreWithScores(key, 0, cursorTime, cursorOffset, 1);
      hasMore = probe != null && !probe.isEmpty();
    }
    views.enrich(records, userId);
    Long nextTime = cursorTime == Long.MAX_VALUE ? lastTime : Long.valueOf(cursorTime);
    int nextOffset = records.isEmpty() && cursorTime == Long.MAX_VALUE ? start : cursorOffset;
    return new FeedResult(records, nextTime, nextOffset, hasMore);
  }

  private static Long parseId(String value) {
    // 只接受 ZSet 中写入的作品 id；损坏成员被忽略并可被后续写入覆盖。
    try {
      long id = Long.parseLong(value);
      return id > 0 ? id : null;
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  private String key(long userId) {
    return prefix + "community:feed:" + userId;
  }
}
