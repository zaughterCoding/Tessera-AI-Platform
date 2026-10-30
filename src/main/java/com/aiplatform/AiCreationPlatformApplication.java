package com.aiplatform;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AiCreationPlatformApplication {
  public static void main(String[] args) {
    // 在加载连接池和时间配置前统一时区，避免开发机设置改变持久时间含义。
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(AiCreationPlatformApplication.class, args);
  }
}
