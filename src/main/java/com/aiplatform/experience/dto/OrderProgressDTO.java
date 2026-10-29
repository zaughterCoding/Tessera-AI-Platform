package com.aiplatform.experience.dto;

import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.messaging.dto.OrderTaskDTO;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import java.util.List;
import lombok.Data;

@Data
public class OrderProgressDTO {
  private PurchaseOrderDTO order;
  private String reservationState;
  private Boolean stockReleased;
  private Integer reservationAttempts;
  private List<OrderTaskDTO> tasks;

  public static OrderProgressDTO of(PurchaseOrderDTO order,
      CampaignReservation reservation, List<OrderTaskDTO> tasks) {
    OrderProgressDTO view = new OrderProgressDTO();
    view.setOrder(order);
    if (reservation != null) {
      view.setReservationState(reservation.getState());
      view.setStockReleased(reservation.getStockReleased());
      view.setReservationAttempts(reservation.getAttempts());
    }
    view.setTasks(tasks);
    return view;
  }
}
