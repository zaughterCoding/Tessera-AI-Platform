package com.aiplatform.catalog.service;

import com.aiplatform.catalog.dto.ApplicationDetailDTO;
import com.aiplatform.catalog.dto.ApplicationSearchForm;
import com.aiplatform.catalog.dto.ApplicationSummaryDTO;
import com.aiplatform.common.api.Result;
import java.util.List;

public interface IApplicationService {
  Result<List<ApplicationSummaryDTO>> search(ApplicationSearchForm form);

  Result<ApplicationDetailDTO> detail(Long id);
}
