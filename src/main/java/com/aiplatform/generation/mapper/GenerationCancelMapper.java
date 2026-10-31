package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.GenerationCancelRequest;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationCancelMapper {
  int insertIgnore(@Param("taskId") long taskId, @Param("userId") long userId,
      @Param("reason") String reason);

  GenerationCancelRequest selectByTaskId(@Param("taskId") long taskId);

  GenerationCancelRequest selectByTaskIdForUpdate(@Param("taskId") long taskId);

  List<GenerationCancelRequest> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

  /** 到期或租约过期的请求由单个工作者认领；渠道调用在事务提交后进行。 */
  List<GenerationCancelRequest> selectDue(@Param("limit") int limit);

  int claim(@Param("id") long id, @Param("owner") String owner,
      @Param("leaseSeconds") int leaseSeconds);

  int markRetry(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("error") String error,
      @Param("delaySeconds") int delaySeconds);

  int finish(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("state") String state,
      @Param("outcome") String outcome);

  /** 结算或可信终态到达时关闭仍开放的请求，不需要工作者租约。 */
  int resolveOpen(@Param("taskId") long taskId, @Param("state") String state,
      @Param("outcome") String outcome);

  int hasOpen(@Param("taskId") long taskId);

  long countByState(@Param("state") String state);

  List<GenerationCancelRequest> selectRecent(@Param("state") String state,
      @Param("limit") int limit, @Param("offset") int offset);
}
