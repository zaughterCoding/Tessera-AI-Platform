package com.aiplatform.generation;

/** 任务账务状态只由结算服务推进；执行状态与账务状态不能互相推断。 */
public final class GenerationAccountingState {
  public static final String FROZEN = "FROZEN";
  public static final String CONSUMED = "CONSUMED";
  public static final String RELEASED = "RELEASED";

  private GenerationAccountingState() {
  }
}
