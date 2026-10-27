package com.aiplatform.catalog.service;

import com.aiplatform.catalog.dto.CategoryDTO;
import com.aiplatform.common.api.Result;
import java.util.List;

public interface ICategoryService {
  Result<List<CategoryDTO>> listCategories();

  List<CategoryDTO> cachedList();
}
