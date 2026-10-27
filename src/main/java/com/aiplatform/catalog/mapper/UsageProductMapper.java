package com.aiplatform.catalog.mapper;

import com.aiplatform.catalog.dto.ProductAvailability;
import com.aiplatform.catalog.entity.UsageProduct;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UsageProductMapper extends BaseMapper<UsageProduct> {
  @Select("SELECT id FROM usage_product WHERE application_id=#{applicationId} AND status=1"
      + " ORDER BY id")
  List<Long> selectActiveIds(@Param("applicationId") Long applicationId);

  List<ProductAvailability> selectAvailability(@Param("ids") List<Long> ids);
}
