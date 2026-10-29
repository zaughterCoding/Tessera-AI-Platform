package com.aiplatform.messaging;

/** 投影可以一次完成，也可以把剩余批次留成可恢复的续传任务。 */
public final class ProjectionResult {
  private static final ProjectionResult COMPLETE = new ProjectionResult(false, 0);

  private final boolean continuation;
  private final long cursor;

  private ProjectionResult(boolean continuation, long cursor) {
    this.continuation = continuation;
    this.cursor = cursor;
  }

  public static ProjectionResult complete() {
    return COMPLETE;
  }

  public static ProjectionResult continueAt(long cursor) {
    if (cursor <= 0) {
      throw new IllegalArgumentException("续传游标必须为正数");
    }
    return new ProjectionResult(true, cursor);
  }

  public boolean isContinuation() {
    return continuation;
  }

  public long cursor() {
    return cursor;
  }
}
