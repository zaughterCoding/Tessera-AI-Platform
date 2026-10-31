package com.aiplatform.generation.service;

import com.aiplatform.common.api.Result;
import com.aiplatform.generation.dto.GenerationStatusDTO;
import com.aiplatform.generation.dto.GenerationTaskDTO;
import com.aiplatform.generation.dto.GenerationTaskForm;
import java.util.List;

public interface IGenerationTaskService {
  GenerationStatusDTO status();

  /** 幂等提交；同一请求键与摘要返回原任务，不同摘要返回冲突。 */
  GenerationTaskDTO submit(long userId, GenerationTaskForm form);

  Result<List<GenerationTaskDTO>> list(long userId, int page, int size);

  GenerationTaskDTO detail(long userId, long taskId);
}
