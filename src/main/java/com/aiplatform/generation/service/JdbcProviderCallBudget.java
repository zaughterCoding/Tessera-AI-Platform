package com.aiplatform.generation.service;

import com.aiplatform.generation.provider.ProviderCallBudget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 真实调用额度以数据库行为唯一事实：条件更新失败时不放行，也不把失败当成已用额度。 */
@Slf4j
@Component
public class JdbcProviderCallBudget implements ProviderCallBudget {
  private final JdbcTemplate jdbc;

  public JdbcProviderCallBudget(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean tryReserve(String channelCode, int limit) {
    if (channelCode == null || limit <= 0) {
      return false;
    }
    try {
      jdbc.update("INSERT IGNORE INTO provider_call_budget(channel_code, calls_used) "
          + "VALUES (?, 0)", channelCode);
      int updated = jdbc.update("UPDATE provider_call_budget SET calls_used = calls_used + 1 "
          + "WHERE channel_code = ? AND calls_used < ?", channelCode, limit);
      return updated == 1;
    } catch (DataAccessException failure) {
      log.warn("真实调用额度不可判定，拒绝本次外部提交，channel={}", channelCode);
      return false;
    }
  }

  @Override
  public int used(String channelCode) {
    try {
      Integer used = jdbc.queryForObject(
          "SELECT calls_used FROM provider_call_budget WHERE channel_code = ?",
          Integer.class, channelCode);
      return used == null ? 0 : used;
    } catch (EmptyResultDataAccessException missing) {
      // 尚未发生真实提交时没有计数行，等于从未使用。
      return 0;
    } catch (DataAccessException failure) {
      return -1;
    }
  }
}
