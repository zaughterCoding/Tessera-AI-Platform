package com.aiplatform.operations.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理写操作的审计标记；只记录对象身份与结果，不记录请求体。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {
  String action();

  String objectType();

  /** 路径变量名；没有对象标识的批量操作可留空。 */
  String objectIdParam() default "id";
}
