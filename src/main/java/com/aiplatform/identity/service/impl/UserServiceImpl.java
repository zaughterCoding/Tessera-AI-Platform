package com.aiplatform.identity.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.LoginFormDTO;
import com.aiplatform.identity.dto.ProfileFormDTO;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.entity.User;
import com.aiplatform.identity.mapper.UserMapper;
import com.aiplatform.identity.security.RateLimiter;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.identity.service.IUserService;
import com.aiplatform.identity.session.RedisScripts;
import com.aiplatform.identity.session.SessionStore;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
  private static final DefaultRedisScript<Long> CONSUME = RedisScripts.number("consume-code.lua");
  private static final SecureRandom RANDOM = new SecureRandom();
  private final StringRedisTemplate redis;
  private final RateLimiter limits;
  private final SessionStore sessions;
  private final TransactionTemplate transactions;
  private final String prefix;
  private final boolean demoEnabled;

  public UserServiceImpl(StringRedisTemplate redis, RateLimiter limits, SessionStore sessions,
      TransactionTemplate transactions, Environment environment,
      @Value("${app.redis.key-prefix}") String prefix,
      @Value("${app.identity.demo-code-enabled}") boolean demoEnabled) {
    Set<String> profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
    if (demoEnabled && (profiles.isEmpty()
        || !new HashSet<>(Arrays.asList("local", "test")).containsAll(profiles))) {
      throw new IllegalArgumentException("验证码演示只允许在显式 local/test 配置下启用");
    }
    this.redis = redis;
    this.limits = limits;
    this.sessions = sessions;
    this.transactions = transactions;
    this.prefix = prefix;
    this.demoEnabled = demoEnabled;
  }

  @Override
  public Result<Map<String, String>> sendCode(String phone) {
    validatePhone(phone);
    if (!demoEnabled) {
      throw new ApiException(ErrorCode.DELIVERY_UNAVAILABLE);
    }
    if (!limits.allow("code-phone:" + phone, 1, 60000)) {
      throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
    }
    String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1000000));
    redis.opsForValue().set(prefix + "login:code:" + phone, code, Duration.ofSeconds(120));
    Map<String, String> delivery = new HashMap<>();
    delivery.put("delivery", "DEMO");
    delivery.put("demoCode", code);
    return Result.ok(delivery);
  }

  @Override
  public Result<String> login(LoginFormDTO form) {
    validatePhone(form.getPhone());
    if (!limits.allow("login-phone:" + form.getPhone(), 5, 120000)) {
      throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
    }
    if (form.getCode() == null || !form.getCode().matches("[0-9]{6}")) {
      throw new ApiException(ErrorCode.INVALID_CODE);
    }
    if (!Long.valueOf(1).equals(redis.execute(CONSUME,
        Collections.singletonList(prefix + "login:code:" + form.getPhone()), form.getCode()))) {
      throw new ApiException(ErrorCode.INVALID_CODE);
    }
    User user = lambdaQuery().eq(User::getPhone, form.getPhone()).one();
    if (user == null) {
      try {
        user = createUserWithPhone(form.getPhone());
      } catch (DuplicateKeyException concurrentCreation) {
        user = lambdaQuery().eq(User::getPhone, form.getPhone()).one();
      }
    }
    if (user == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return Result.ok(sessions.create(user.getId()));
  }

  private User createUserWithPhone(String phone) {
    // 身份与默认角色同事务提交；唯一约束冲突在事务结束后读取竞争者的结果。
    return transactions.execute(tx -> {
      User user = new User();
      user.setPhone(phone);
      user.setNickName("creator_" + UUID.randomUUID().toString().substring(0, 10));
      if (!save(user) || baseMapper.insertUserRole(user.getId()) != 1) {
        throw new ApiException(ErrorCode.UNAVAILABLE);
      }
      return user;
    });
  }

  @Override
  public UserDTO authenticate(String authorization) {
    Long id = sessions.refresh(authorization);
    if (id == null) {
      return null;
    }
    User user = getById(id);
    if (user == null) {
      sessions.revoke(authorization);
      return null;
    }
    UserDTO view = view(user);
    // 会话只保存身份，角色每次核对数据库，撤销管理角色立即生效。
    if (!view.getRoles().contains("USER") && !view.getRoles().contains("ADMIN")) {
      return null;
    }
    return view;
  }

  @Override
  public Result<Void> logout(String authorization) {
    sessions.revoke(authorization);
    UserHolder.removeUser();
    return Result.ok();
  }

  @Override
  public Result<UserDTO> queryUserById(Long userId) {
    if (!currentUser().getId().equals(userId)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    return Result.ok(currentUser());
  }

  @Override
  public Result<Void> updateProfile(ProfileFormDTO form) {
    if (baseMapper.updateNickName(currentUser().getId(), form.getNickName()) != 1) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return Result.ok();
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public UserDTO findForAdministration(Long userId) {
    if (!currentUser().getRoles().contains("ADMIN")) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    User user = getById(userId);
    if (user == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return view(user);
  }

  private UserDTO view(User user) {
    return new UserDTO(user.getId(), user.getNickName(), user.getIcon(),
        Collections.unmodifiableSet(new HashSet<>(baseMapper.selectRoles(user.getId()))));
  }

  private UserDTO currentUser() {
    if (UserHolder.getUser() == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return UserHolder.getUser();
  }

  private void validatePhone(String phone) {
    if (phone == null || !phone.matches("1[3-9][0-9]{9}")) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
  }
}
