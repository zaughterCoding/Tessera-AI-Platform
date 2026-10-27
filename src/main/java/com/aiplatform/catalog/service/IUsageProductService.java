package com.aiplatform.catalog.service;

import com.aiplatform.catalog.dto.ProductAdminUpdateForm;
import com.aiplatform.catalog.dto.UsageProductDTO;
import com.aiplatform.common.api.Result;
import java.util.List;

public interface IUsageProductService {
  Result<List<UsageProductDTO>> listByApplication(Long applicationId);

  Result<UsageProductDTO> detail(Long id);

  void updateProduct(Long id, ProductAdminUpdateForm form, Long actorId);
}
