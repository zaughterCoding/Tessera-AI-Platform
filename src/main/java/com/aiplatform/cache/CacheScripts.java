package com.aiplatform.cache;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class CacheScripts {
  private CacheScripts() {}

  public static DefaultRedisScript<Long> number(String path) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(path));
    script.setResultType(Long.class);
    return script;
  }

  /** 只读脚本返回扁平数组，由调用方按固定字段步长解析。 */
  @SuppressWarnings("rawtypes")
  public static DefaultRedisScript<List> list(String path) {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(path));
    script.setResultType(List.class);
    return script;
  }
}
