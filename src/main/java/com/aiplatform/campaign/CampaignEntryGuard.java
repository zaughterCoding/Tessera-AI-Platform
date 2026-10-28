package com.aiplatform.campaign;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Redis 活动状态不可信时实际关闭入口；恢复重建成功后才重新放行。 */
@Slf4j
@Component
public class CampaignEntryGuard {
  private final ConcurrentMap<Long, String> blocked = new ConcurrentHashMap<>();

  public void block(long campaignId, String reason) {
    String previous = blocked.put(campaignId, reason);
    if (previous == null) {
      log.error("活动入口已关闭，campaignId={}，reason={}", campaignId, reason);
    }
  }

  public void unblock(long campaignId) {
    if (blocked.remove(campaignId) != null) {
      log.info("活动入口已恢复，campaignId={}", campaignId);
    }
  }

  public boolean isBlocked(long campaignId) {
    return blocked.containsKey(campaignId);
  }

  /** 入口关闭期间拒绝受理，不能只依赖日志或页面提示。 */
  public void requireOpen(long campaignId) {
    if (isBlocked(campaignId)) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
  }
}
