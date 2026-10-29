package com.aiplatform.community;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** 社区时间口径与批量大小在启动时校验，避免运行期出现隐式默认。 */
@Configuration
public class CommunityConfig {
  private final ZoneId signInZone;
  private final int feedBatch;
  private final int feedPageMax;
  private final long signInTtlSeconds;

  public CommunityConfig(
      @Value("${app.community.signin-zone:Asia/Shanghai}") String signInZone,
      @Value("${app.community.feed-batch:200}") int feedBatch,
      @Value("${app.community.feed-page-max:50}") int feedPageMax,
      @Value("${app.community.signin-ttl-seconds:3456000}") long signInTtlSeconds) {
    try {
      this.signInZone = ZoneId.of(signInZone);
    } catch (RuntimeException invalid) {
      throw new IllegalStateException("签到时区不合法", invalid);
    }
    if (feedBatch < 1 || feedBatch > 1000) {
      throw new IllegalStateException("粉丝分批大小必须在 1～1000 之间");
    }
    if (feedPageMax < 1 || feedPageMax > 200) {
      throw new IllegalStateException("Feed 单页上限必须在 1～200 之间");
    }
    if (signInTtlSeconds < 86400) {
      throw new IllegalStateException("签到位图保留时间不能少于 1 天");
    }
    this.feedBatch = feedBatch;
    this.feedPageMax = feedPageMax;
    this.signInTtlSeconds = signInTtlSeconds;
  }

  public ZoneId signInZone() {
    return signInZone;
  }

  public int feedBatch() {
    return feedBatch;
  }

  public int feedPageMax() {
    return feedPageMax;
  }

  public long signInTtlSeconds() {
    return signInTtlSeconds;
  }
}
