package com.aiplatform.generation;

/** 上游尝试状态；终态一旦成立，晚到的运行态与丢弃确认都不能将其反转。 */
public final class ProviderAttemptState {
  public static final String SUBMIT_PENDING = "SUBMIT_PENDING";
  public static final String SUBMITTING = "SUBMITTING";
  public static final String ACCEPTED = "ACCEPTED";
  public static final String RUNNING = "RUNNING";
  public static final String RECONCILING = "RECONCILING";
  public static final String RESULT_PENDING = "RESULT_PENDING";
  public static final String FAILED = "FAILED";
  public static final String CANCELLED = "CANCELLED";
  public static final String REVIEW = "REVIEW";

  private ProviderAttemptState() {
  }

  public static boolean isTerminal(String state) {
    return RESULT_PENDING.equals(state) || FAILED.equals(state) || CANCELLED.equals(state);
  }

  public static String taskState(String attemptState) {
    if (SUBMIT_PENDING.equals(attemptState) || SUBMITTING.equals(attemptState)) {
      return GenerationTaskState.DISPATCHING;
    }
    if (ACCEPTED.equals(attemptState)) {
      return GenerationTaskState.SUBMITTED;
    }
    if (RUNNING.equals(attemptState)) {
      return GenerationTaskState.RUNNING;
    }
    if (RECONCILING.equals(attemptState)) {
      return GenerationTaskState.RECONCILING;
    }
    if (RESULT_PENDING.equals(attemptState)) {
      return GenerationTaskState.RESULT_PENDING;
    }
    if (FAILED.equals(attemptState)) {
      return GenerationTaskState.FAILED;
    }
    if (CANCELLED.equals(attemptState)) {
      return GenerationTaskState.CANCELLED;
    }
    return GenerationTaskState.REVIEW;
  }
}
