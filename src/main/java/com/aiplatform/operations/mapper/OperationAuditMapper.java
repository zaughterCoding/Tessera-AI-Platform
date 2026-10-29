package com.aiplatform.operations.mapper;

import com.aiplatform.operations.entity.OperationAudit;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationAuditMapper extends BaseMapper<OperationAudit> {
  List<OperationAudit> selectAudits(@Param("action") String action,
      @Param("objectType") String objectType,
      @Param("operatorId") Long operatorId,
      @Param("outcome") String outcome,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countAudits(@Param("action") String action,
      @Param("objectType") String objectType,
      @Param("operatorId") Long operatorId,
      @Param("outcome") String outcome);

  long countBefore(@Param("cutoff") LocalDateTime cutoff);

  int deleteBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
