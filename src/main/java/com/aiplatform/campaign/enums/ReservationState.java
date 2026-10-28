package com.aiplatform.campaign.enums;

/** 预约资格状态；只有 SUCCESS 代表对应订单已创建。 */
public enum ReservationState {
  PROCESSING,
  SUCCESS,
  REVIEW,
  FAILED
}
