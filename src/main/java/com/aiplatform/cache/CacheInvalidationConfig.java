package com.aiplatform.cache;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class CacheInvalidationConfig {
  @Bean
  public RedisMessageListenerContainer cacheInvalidations(
      RedisConnectionFactory connection,
      CacheClient cache,
      @Value("${app.namespace}") String namespace) {
    RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
    listener.setConnectionFactory(connection);
    listener.setRecoveryInterval(1_000L);
    listener.addMessageListener(
        (message, pattern) ->
            cache.evictLocal(new String(message.getBody(), StandardCharsets.UTF_8)),
        new ChannelTopic(namespace + ":cache:invalidate"));
    return listener;
  }
}
