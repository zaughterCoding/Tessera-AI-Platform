package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.ProviderChannel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProviderChannelMapper extends BaseMapper<ProviderChannel> {
  ProviderChannel selectByCode(@Param("code") String code);

  /** 队列占用通过条件更新取得，满额时返回 0；调用方负责回滚整个提交事务。 */
  int reserveQueueSlot(@Param("code") String code);

  int releaseQueue(@Param("channelId") long channelId);

  /** 同时扣减队列并占用上游预算；容量不足或队列不一致时返回 0。 */
  int occupyInFlight(@Param("channelId") long channelId);

  /** 只有已确认终态才能释放；重复释放受 in_flight > 0 与状态迁移双重保护。 */
  int releaseInFlight(@Param("channelId") long channelId);

  /** 只同步上限，不触碰在途与队列计数。 */
  int syncLimits(@Param("code") String code, @Param("maxInFlight") int maxInFlight,
      @Param("queueCapacity") int queueCapacity,
      @Param("queueTimeoutSeconds") int queueTimeoutSeconds);
}
