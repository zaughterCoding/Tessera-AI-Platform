package com.aiplatform.experience.service;

import com.aiplatform.experience.dto.CacheTraceDTO;
import com.aiplatform.experience.dto.OrderProgressDTO;
import java.util.Map;

public interface IExperienceService {
  Map<String, Object> capabilities();

  CacheTraceDTO traceCache(long applicationId);

  OrderProgressDTO orderProgress(long userId, long orderId);
}
