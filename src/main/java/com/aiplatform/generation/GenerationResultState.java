package com.aiplatform.generation;

/** 单个结果文件的生命周期；只有完整落盘并登记的记录才可被本人下载。 */
public final class GenerationResultState {
  public static final String PENDING = "PENDING";
  public static final String SAVING = "SAVING";
  public static final String SAVED = "SAVED";
  public static final String DELIVERED = "DELIVERED";
  public static final String FAILED = "FAILED";

  private GenerationResultState() {
  }
}
