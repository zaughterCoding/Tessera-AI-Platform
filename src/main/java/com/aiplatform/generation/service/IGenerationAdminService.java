package com.aiplatform.generation.service;

import com.aiplatform.generation.dto.GenerationAdminActionResultDTO;
import com.aiplatform.generation.dto.GenerationAdminDeliveryDTO;
import com.aiplatform.generation.dto.GenerationAdminOverviewDTO;
import com.aiplatform.generation.dto.GenerationAdminTaskDTO;
import com.aiplatform.generation.dto.GenerationReconcileResultDTO;
import com.aiplatform.generation.dto.GenerationUserSchedulingDTO;
import com.aiplatform.generation.dto.GenerationUserSchedulingForm;
import com.aiplatform.generation.dto.SimulatorFaultForm;
import java.util.List;

/** 管理入口只提供只读排障与安全恢复；不提供改状态、改余额或伪造上游成功的操作。 */
public interface IGenerationAdminService {
  GenerationAdminOverviewDTO overview();

  List<GenerationAdminTaskDTO> tasks(String state, int limit, int offset);

  GenerationAdminTaskDTO task(long taskId);

  GenerationReconcileResultDTO reconcile(long taskId);

  GenerationAdminDeliveryDTO delivery(long taskId);

  /** 仅重置保存失败的结果事实；不改变任务决定、余额或上游引用。 */
  GenerationAdminActionResultDTO retryResult(long taskId);

  /** 按已有可信事实补做唯一结算；无法证明时保持冻结。 */
  GenerationAdminActionResultDTO recoverSettlement(long taskId);

  /** 管理代办取消仍登记持久请求，是否真正取消由上游确认决定。 */
  GenerationAdminActionResultDTO cancelTask(long taskId, String reason);

  GenerationUserSchedulingDTO userScheduling(long userId);

  /** 调低上限只拒绝新提交，已受理的任务继续执行。 */
  GenerationUserSchedulingDTO updateUserScheduling(long userId,
      GenerationUserSchedulingForm form);

  /** 仅当演示故障控制开关显式开启时可用。 */
  void addSimulatorFault(SimulatorFaultForm form);
}
