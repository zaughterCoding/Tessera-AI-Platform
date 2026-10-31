package com.aiplatform.generation;

/** 任务执行状态；结果交付后进入唯一终态，账务状态单独表达。 */
public final class GenerationTaskState {
  public static final String QUEUED = "QUEUED";
  public static final String DISPATCHING = "DISPATCHING";
  public static final String SUBMITTED = "SUBMITTED";
  public static final String RUNNING = "RUNNING";
  public static final String RECONCILING = "RECONCILING";
  public static final String RESULT_PENDING = "RESULT_PENDING";
  public static final String FAILED_PENDING_FOLLOWUP = "FAILED_PENDING_FOLLOWUP";
  public static final String CANCELLED_PENDING_FOLLOWUP = "CANCELLED_PENDING_FOLLOWUP";
  public static final String QUEUE_TIMEOUT = "QUEUE_TIMEOUT";
  public static final String REVIEW = "REVIEW";
  public static final String CANCEL_REQUESTED = "CANCEL_REQUESTED";
  public static final String SUCCEEDED = "SUCCEEDED";
  public static final String FAILED = "FAILED";
  public static final String CANCELLED = "CANCELLED";

  private GenerationTaskState() {
  }

  public static boolean isUpstreamFinal(String state) {
    return RESULT_PENDING.equals(state)
        || FAILED_PENDING_FOLLOWUP.equals(state)
        || CANCELLED_PENDING_FOLLOWUP.equals(state)
        || QUEUE_TIMEOUT.equals(state)
        || SUCCEEDED.equals(state)
        || FAILED.equals(state)
        || CANCELLED.equals(state);
  }

  public static boolean isSettledFinal(String state) {
    return SUCCEEDED.equals(state) || FAILED.equals(state) || CANCELLED.equals(state);
  }

  public static boolean canApplyUpstreamFact(String state) {
    return !isUpstreamFinal(state);
  }
}
