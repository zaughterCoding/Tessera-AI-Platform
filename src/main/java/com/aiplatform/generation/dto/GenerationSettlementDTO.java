package com.aiplatform.generation.dto;

import com.aiplatform.entitlement.entity.GenerationSettlement;
import java.time.LocalDateTime;
import lombok.Data;

/** 结算说明：成功为消费，失败或取消按权益包状态退回可用或过期。 */
@Data
public class GenerationSettlementDTO {
  private Long id;
  private Long taskId;
  private String kind;
  private String releaseTarget;
  private Integer units;
  private Long ledgerId;
  private String description;
  private LocalDateTime createdAt;

  public static GenerationSettlementDTO from(GenerationSettlement settlement) {
    if (settlement == null) {
      return null;
    }
    GenerationSettlementDTO view = new GenerationSettlementDTO();
    view.setId(settlement.getId());
    view.setTaskId(settlement.getTaskId());
    view.setKind(settlement.getKind());
    view.setReleaseTarget(settlement.getReleaseTarget());
    view.setUnits(settlement.getUnits());
    view.setLedgerId(settlement.getLedgerId());
    view.setCreatedAt(settlement.getCreatedAt());
    view.setDescription(describe(settlement));
    return view;
  }

  private static String describe(GenerationSettlement settlement) {
    if ("CONSUME".equals(settlement.getKind())) {
      return "结果已交付，消费 " + settlement.getUnits() + " 次";
    }
    if ("EXPIRED".equals(settlement.getReleaseTarget())) {
      return "任务结束，权益包已过期，释放 " + settlement.getUnits() + " 次到已过期";
    }
    return "任务结束，释放 " + settlement.getUnits() + " 次回可用余额";
  }
}
