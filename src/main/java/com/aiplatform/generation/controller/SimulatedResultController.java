package com.aiplatform.generation.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.generation.entity.SimulatedProviderJob;
import com.aiplatform.generation.mapper.SimulatedProviderMapper;
import com.aiplatform.generation.provider.SimulatedResultFiles;
import com.aiplatform.identity.security.PublicEndpoint;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟上游的受控下载端点：仅对已成功且使用真实文件引用的任务签发，
 * 令牌按任务与位置绑定。该端点只属于模拟渠道，不暴露任何用户或业务数据。
 */
@RestController
@RequestMapping("/internal/simulated/results")
public class SimulatedResultController {
  private static final int MAX_POSITION = 99;

  private final SimulatedProviderMapper jobs;
  private final SimulatedResultFiles resultFiles;

  public SimulatedResultController(SimulatedProviderMapper jobs,
      SimulatedResultFiles resultFiles) {
    this.jobs = jobs;
    this.resultFiles = resultFiles;
  }

  @PublicEndpoint
  @GetMapping("/{providerTaskId}/{position}")
  public ResponseEntity<byte[]> download(@PathVariable String providerTaskId,
      @PathVariable String position, @RequestParam(value = "token", required = false)
      String token) {
    int index = parsePosition(position);
    SimulatedProviderJob job = jobs.selectJobByProviderTaskId(providerTaskId);
    if (job == null || !"SUCCEEDED".equals(job.getState())
        || !resultFiles.deliveredUrl(providerTaskId, job.getResultRef())
        || index >= outputCount(job)
        || !resultFiles.tokenMatches(providerTaskId, index, token)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    byte[] content = resultFiles.png(providerTaskId, index);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .contentLength(content.length)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(content);
  }

  private int parsePosition(String value) {
    if (value == null || !value.matches("\\d{1,2}")) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    int position = Integer.parseInt(value);
    if (position < 0 || position > MAX_POSITION) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return position;
  }

  private int outputCount(SimulatedProviderJob job) {
    return job.getOutputCount() == null || job.getOutputCount() < 1
        ? 1 : job.getOutputCount();
  }
}
