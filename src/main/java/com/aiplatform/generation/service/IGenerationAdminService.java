package com.aiplatform.generation.service;

import com.aiplatform.generation.dto.GenerationAdminOverviewDTO;
import com.aiplatform.generation.dto.GenerationAdminTaskDTO;
import com.aiplatform.generation.dto.GenerationReconcileResultDTO;
import com.aiplatform.generation.dto.SimulatorFaultForm;
import java.util.List;

/** 管理入口只提供只读排障与安全恢复；不提供改状态、改余额或伪造上游成功的操作。 */
public interface IGenerationAdminService {
  GenerationAdminOverviewDTO overview();

  List<GenerationAdminTaskDTO> tasks(String state, int limit, int offset);

  GenerationAdminTaskDTO task(long taskId);

  GenerationReconcileResultDTO reconcile(long taskId);

  /** 仅当演示故障控制开关显式开启时可用。 */
  void addSimulatorFault(SimulatorFaultForm form);
}
