package com.aiplatform.campaign.service.impl;

import com.aiplatform.cache.CacheScripts;
import com.aiplatform.campaign.CampaignKeys;
import com.aiplatform.campaign.CampaignMessage;
import com.aiplatform.campaign.CampaignStreamSnapshot;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.service.ICampaignRedisService;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisCallback;
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
  private static final DefaultRedisScript<Long> STREAM_LAG =
      CacheScripts.number("campaign-stream-lag.lua");
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
  public long admit(long orderId, long userId, long campaignId, String requestKey) {
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
        requestKey, String.valueOf(requestTtlSeconds));
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
    Long length = redis.opsForStream().size(keys.stream());
    snapshot.setLength(length == null ? 0 : length);
    StreamInfo.XInfoGroups groups = redis.opsForStream().groups(keys.stream());
    if (groups != null) {
      for (int index = 0; index < groups.size(); index++) {
        StreamInfo.XInfoGroup group = groups.get(index);
        if (keys.group().equals(group.groupName())) {
          snapshot.setGroupExists(true);
          snapshot.setPending(group.pendingCount() == null ? 0 : group.pendingCount());
          snapshot.setLastDeliveredId(group.lastDeliveredId());
          break;
        }
      }
    }
    if (!snapshot.isGroupExists()) {
      return snapshot;
    }
    Long lag = redis.execute(STREAM_LAG, Arrays.asList(keys.stream()), keys.group());
    snapshot.setLag(lag == null ? -1 : lag);
    PendingMessages oldest = redis.opsForStream().pending(
        keys.stream(), keys.group(), Range.unbounded(), 1L);
    if (oldest != null && !oldest.isEmpty()) {
      snapshot.setOldestPendingAgeMillis(
          oldest.get(0).getElapsedTimeSinceLastDelivery().toMillis());
    }
    return snapshot;
  }

  @Override
  public long trimBelow(String minId) {
    Long removed = redis.execute((RedisCallback<Long>) connection -> {
      Object result = connection.execute("XTRIM",
          keys.stream().getBytes(StandardCharsets.UTF_8),
          "MINID".getBytes(StandardCharsets.UTF_8),
          minId.getBytes(StandardCharsets.UTF_8));
      return result instanceof Long ? (Long) result : 0L;
    });
    return removed == null ? 0L : removed;
  }
}
