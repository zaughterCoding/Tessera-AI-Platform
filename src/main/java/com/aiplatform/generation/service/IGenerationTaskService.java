package com.aiplatform.generation.service;

import com.aiplatform.common.api.Result;
import com.aiplatform.generation.dto.GenerationStatusDTO;
import com.aiplatform.generation.dto.GenerationTaskDTO;
import com.aiplatform.generation.dto.GenerationTaskForm;
import com.aiplatform.media.dto.MediaContent;
import java.util.List;

public interface IGenerationTaskService {
  GenerationStatusDTO status();

  /** 幂等提交；同一请求键与摘要返回原任务，不同摘要返回冲突。 */
  GenerationTaskDTO submit(long userId, GenerationTaskForm form);

  Result<List<GenerationTaskDTO>> list(long userId, int page, int size);

  GenerationTaskDTO detail(long userId, long taskId);

  /** 私有结果下载：只对本人任务中已保存并登记的结果开放。 */
  MediaContent downloadResult(long userId, long taskId, long resultId);

  /** 登记取消意图；排队任务可能立即完成，执行中任务等待后台确认。 */
  GenerationTaskDTO cancel(long userId, long taskId, String reason);
}
