package com.aiplatform.generation;

/** 任务执行状态；账务状态在本阶段始终为 FROZEN，二者不能互相推断。 */
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

  private GenerationTaskState() {
  }

  public static boolean isUpstreamFinal(String state) {
    return RESULT_PENDING.equals(state)
        || FAILED_PENDING_FOLLOWUP.equals(state)
        || CANCELLED_PENDING_FOLLOWUP.equals(state)
        || QUEUE_TIMEOUT.equals(state);
  }

  public static boolean canApplyUpstreamFact(String state) {
    return !isUpstreamFinal(state);
  }
}
