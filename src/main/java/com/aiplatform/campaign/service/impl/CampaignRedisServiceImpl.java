package com.aiplatform.campaign.service.impl;

import com.aiplatform.cache.CacheScripts;
import com.aiplatform.campaign.CampaignKeys;
import com.aiplatform.campaign.CampaignMessage;
import com.aiplatform.campaign.CampaignStreamSnapshot;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.service.ICampaignRedisService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class CampaignRedisServiceImpl implements ICampaignRedisService {
  private static final DefaultRedisScript<Long> ADMIT =
      CacheScripts.number("campaign-admit.lua");
  private static final DefaultRedisScript<Long> RELEASE =
      CacheScripts.number("campaign-release.lua");
  private static final DefaultRedisScript<Long> REBUILD =
      CacheScripts.number("campaign-rebuild.lua");
  private static final DefaultRedisScript<Long> STREAM_TRIM =
      CacheScripts.number("campaign-stream-trim.lua");
  @SuppressWarnings("rawtypes")
  private static final DefaultRedisScript<List> STREAM_GROUPS =
      CacheScripts.list("campaign-stream-groups.lua");
  private final StringRedisTemplate redis;
  private final CampaignKeys keys;
  private final int requestTtlSeconds;

  public CampaignRedisServiceImpl(
      StringRedisTemplate redis,
      CampaignKeys keys,
      @Value("${app.campaign.request-ttl-seconds:2592000}") int requestTtlSeconds) {
    this.redis = redis;
    this.keys = keys;
    this.requestTtlSeconds = requestTtlSeconds;
  }

  @Override
  public long admit(long orderId, long userId, long campaignId, String requestKey,
      int expectedGeneration, int initialStock) {
    Long result = redis.execute(ADMIT,
        Arrays.asList(
            keys.stock(campaignId),
            keys.buyers(campaignId),
            keys.stream(),
            keys.activity(campaignId),
            keys.requests(campaignId),
            keys.request(orderId),
            keys.requestKey(campaignId, userId, requestKey)),
        String.valueOf(orderId), String.valueOf(userId), String.valueOf(campaignId),
        requestKey, String.valueOf(requestTtlSeconds),
        String.valueOf(expectedGeneration), String.valueOf(initialStock));
    return result == null ? -1L : result;
  }

  @Override
  public String existingOrderForRequestKey(
      long campaignId, long userId, String requestKey) {
    return redis.opsForValue().get(keys.requestKey(campaignId, userId, requestKey));
  }

  @Override
  public String existingOrderForUser(long campaignId, long userId) {
    Object value = redis.opsForHash().get(keys.requests(campaignId), String.valueOf(userId));
    return value == null ? null : String.valueOf(value);
  }

  @Override
  public boolean isBuyer(long campaignId, long userId) {
    return Boolean.TRUE.equals(
        redis.opsForSet().isMember(keys.buyers(campaignId), String.valueOf(userId)));
  }

  @Override
  public Map<String, String> requestState(long orderId) {
    Map<Object, Object> raw = redis.opsForHash().entries(keys.request(orderId));
    Map<String, String> state = new HashMap<>();
    for (Map.Entry<Object, Object> entry : raw.entrySet()) {
      state.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
    }
    return state;
  }

  @Override
  public Long redisStock(long campaignId) {
    String value = redis.opsForValue().get(keys.stock(campaignId));
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException failure) {
      throw new IllegalStateException("活动库存状态不可信", failure);
    }
  }

  @Override
  public Integer redisGeneration(long campaignId) {
    Object value = redis.opsForHash().get(keys.activity(campaignId), "generation");
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(String.valueOf(value));
    } catch (NumberFormatException failure) {
      throw new IllegalStateException("活动代次状态不可信", failure);
    }
  }

  @Override
  public boolean setActivityState(long campaignId, String state) {
    String key = keys.activity(campaignId);
    if (!Boolean.TRUE.equals(redis.hasKey(key))) {
      return false;
    }
    redis.opsForHash().put(key, "state", state);
    return true;
  }

  @Override
  public String activityState(long campaignId) {
    Object value = redis.opsForHash().get(keys.activity(campaignId), "state");
    return value == null ? null : String.valueOf(value);
  }

  @Override
  public boolean markRequestState(long orderId, String state) {
    String key = keys.request(orderId);
    if (!Boolean.TRUE.equals(redis.hasKey(key))) {
      return false;
    }
    redis.opsForHash().put(key, "state", state);
    return true;
  }

  @Override
  public long release(long campaignId, long userId, long orderId, int generation,
      String targetState, String requestKey, int initialStock) {
    String mapKey = requestKey == null ? ""
        : keys.requestKey(campaignId, userId, requestKey);
    Long result = redis.execute(RELEASE,
        Arrays.asList(
            keys.stock(campaignId),
            keys.buyers(campaignId),
            keys.requests(campaignId),
            keys.request(orderId),
            keys.activity(campaignId),
            keys.carried(campaignId)),
        String.valueOf(userId), String.valueOf(orderId), String.valueOf(generation),
        targetState, mapKey, String.valueOf(initialStock));
    return result == null ? -1L : result;
  }

  @Override
  public int rebuild(LimitedCampaign campaign, int generation, String state,
      List<CampaignReservation> activeReservations) {
    List<String> args = new ArrayList<>();
    args.add(String.valueOf(campaign.getId()));
    args.add(String.valueOf(generation));
    args.add(String.valueOf(campaign.getRemainingStock()));
    args.add(String.valueOf(campaign.getBeginAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
    args.add(String.valueOf(campaign.getEndAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
    args.add(state);
    for (CampaignReservation reservation : activeReservations) {
      args.add(String.valueOf(reservation.getUserId()));
      args.add(String.valueOf(reservation.getOrderId()));
    }
    Long written = redis.execute(REBUILD,
        Arrays.asList(
            keys.stock(campaign.getId()),
            keys.buyers(campaign.getId()),
            keys.requests(campaign.getId()),
            keys.activity(campaign.getId()),
            keys.carried(campaign.getId())),
        args.toArray());
    return written == null ? -1 : written.intValue();
  }

  @Override
  public String enqueue(CampaignMessage message) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("orderId", String.valueOf(message.getOrderId()));
    fields.put("userId", String.valueOf(message.getUserId()));
    fields.put("campaignId", String.valueOf(message.getCampaignId()));
    fields.put("generation", String.valueOf(message.getGeneration()));
    fields.put("requestKey", message.getRequestKey() == null ? "" : message.getRequestKey());
    RecordId id = redis.opsForStream().add(keys.stream(), fields);
    return id == null ? null : id.getValue();
  }

  @Override
  public CampaignStreamSnapshot snapshot() {
    CampaignStreamSnapshot snapshot = new CampaignStreamSnapshot();
    snapshot.setStreamExists(Boolean.TRUE.equals(redis.hasKey(keys.stream())));
    if (!snapshot.isStreamExists()) {
      return snapshot;
    }
    try {
      Long length = redis.opsForStream().size(keys.stream());
      snapshot.setLength(length == null ? 0 : length);
    } catch (RuntimeException failure) {
      // Stream 类型损坏时无法给出安全水位，按未知处理并阻止裁剪。
      snapshot.setUnknownLagGroups(1);
      snapshot.setAllGroupsCaughtUp(false);
      return snapshot;
    }
    List<?> groups;
    try {
      groups = redis.execute(STREAM_GROUPS, Collections.singletonList(keys.stream()));
    } catch (RuntimeException failure) {
      // 无法枚举全部消费组时不能给出安全水位。
      snapshot.setUnknownLagGroups(1);
      snapshot.setAllGroupsCaughtUp(false);
      return snapshot;
    }
    if (groups == null) {
      snapshot.setUnknownLagGroups(1);
      snapshot.setAllGroupsCaughtUp(false);
      return snapshot;
    }
    for (int index = 0; index + 3 < groups.size(); index += 4) {
      String name = text(groups.get(index));
      long pending = number(groups.get(index + 1), 0);
      long lag = number(groups.get(index + 2), -1);
      String last = text(groups.get(index + 3));
      snapshot.setGroupCount(snapshot.getGroupCount() + 1);
      snapshot.setTotalPending(snapshot.getTotalPending() + pending);
      if (lag < 0) {
        snapshot.setUnknownLagGroups(snapshot.getUnknownLagGroups() + 1);
      } else if (lag > snapshot.getMaxGroupLag()) {
        snapshot.setMaxGroupLag(lag);
      }
      if (keys.group().equals(name)) {
        snapshot.setGroupExists(true);
        snapshot.setPending(pending);
        snapshot.setLag(lag);
        snapshot.setLastDeliveredId(last);
      } else {
        snapshot.setOtherPending(snapshot.getOtherPending() + pending);
      }
      if (last != null && !last.isEmpty()
          && (snapshot.getTrimWatermark() == null
              || compareIds(last, snapshot.getTrimWatermark()) < 0)) {
        snapshot.setTrimWatermark(last);
      }
    }
    // 默认消费组缺失时不能把“其余组已追平”当成安全水位。
    snapshot.setAllGroupsCaughtUp(snapshot.isGroupExists()
        && snapshot.getGroupCount() > 0
        && snapshot.getUnknownLagGroups() == 0
        && snapshot.getMaxGroupLag() == 0
        && snapshot.getTotalPending() == 0);
    if (snapshot.isGroupExists()) {
      PendingMessages oldest = redis.opsForStream().pending(
          keys.stream(), keys.group(), Range.unbounded(), 1L);
      if (oldest != null && !oldest.isEmpty()) {
        snapshot.setOldestPendingAgeMillis(
            oldest.get(0).getElapsedTimeSinceLastDelivery().toMillis());
      }
    }
    return snapshot;
  }

  @Override
  public long trimIfAllSafe() {
    Long result = redis.execute(STREAM_TRIM, Collections.singletonList(keys.stream()),
        keys.group());
    if (result == null || result <= 0) {
      return -1L;
    }
    return result - 1L;
  }

  private String text(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof byte[]) {
      return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
    }
    return String.valueOf(value);
  }

  private long number(Object value, long fallback) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    String text = text(value);
    if (text == null || text.isEmpty()) {
      return fallback;
    }
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException failure) {
      return fallback;
    }
  }

  private int compareIds(String left, String right) {
    int leftDash = left.indexOf('-');
    int rightDash = right.indexOf('-');
    if (leftDash <= 0 || rightDash <= 0) {
      return left.compareTo(right);
    }
    try {
      long leftMillis = Long.parseLong(left.substring(0, leftDash));
      long rightMillis = Long.parseLong(right.substring(0, rightDash));
      if (leftMillis != rightMillis) {
        return leftMillis < rightMillis ? -1 : 1;
      }
      long leftSequence = Long.parseLong(left.substring(leftDash + 1));
      long rightSequence = Long.parseLong(right.substring(rightDash + 1));
      return Long.compare(leftSequence, rightSequence);
    } catch (NumberFormatException failure) {
      return left.compareTo(right);
    }
  }
}
