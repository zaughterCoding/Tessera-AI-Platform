package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.ProviderChannel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProviderChannelMapper extends BaseMapper<ProviderChannel> {
  ProviderChannel selectByCode(@Param("code") String code);

  /** 尝试所属渠道的类型；用于历史尝试在渠道切换后仍按原适配器收敛。 */
  String selectKindByAttemptKey(@Param("attemptKey") String attemptKey);

  /** 启动时把真实渠道的启用状态与能力声明同步到渠道行。 */
  int syncChannelDeclaration(@Param("code") String code, @Param("status") String status,
      @Param("idempotentSubmit") boolean idempotentSubmit,
      @Param("queryByAttempt") boolean queryByAttempt, @Param("cancel") boolean cancel);

  /** 队列占用通过条件更新取得，满额时返回 0；调用方负责回滚整个提交事务。 */
  int reserveQueueSlot(@Param("code") String code);

  int releaseQueue(@Param("channelId") long channelId);

  /** 同时扣减队列并占用上游预算；容量不足或队列不一致时返回 0。自适应时受生效值限制。 */
  int occupyInFlight(@Param("channelId") long channelId, @Param("adaptive") boolean adaptive);

  /** 首次取初始值，之后保留持久值；硬上限调低时一并收紧。 */
  int initEffectiveInFlight(@Param("code") String code, @Param("initial") int initial);

  int countDispatchPaused(@Param("channelId") long channelId);

  int growEffectiveInFlight(@Param("channelId") long channelId,
      @Param("windowSeconds") int windowSeconds);

  int resetAdaptiveWindow(@Param("channelId") long channelId);

  int halveEffectiveInFlight(@Param("channelId") long channelId,
      @Param("pauseSeconds") int pauseSeconds);

  /** 只有已确认终态才能释放；重复释放受 in_flight > 0 与状态迁移双重保护。 */
  int releaseInFlight(@Param("channelId") long channelId);

  int requeueInFlight(@Param("channelId") long channelId);

  /** 只同步上限，不触碰在途与队列计数。 */
  int syncLimits(@Param("code") String code, @Param("maxInFlight") int maxInFlight,
      @Param("queueCapacity") int queueCapacity,
      @Param("queueTimeoutSeconds") int queueTimeoutSeconds);
}
