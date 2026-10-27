package com.aiplatform.catalog.controller;

import com.aiplatform.catalog.dto.ApplicationDetailDTO;
import com.aiplatform.catalog.dto.ApplicationSearchForm;
import com.aiplatform.catalog.dto.ApplicationSummaryDTO;
import com.aiplatform.catalog.dto.CategoryDTO;
import com.aiplatform.catalog.dto.UsageProductDTO;
import com.aiplatform.catalog.service.IApplicationService;
import com.aiplatform.catalog.service.ICategoryService;
import com.aiplatform.catalog.service.IUsageProductService;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.PublicEndpoint;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开只读目录；写入与可靠缓存失效在后续阶段闭环后再开放。 */
@PublicEndpoint
@Validated
@RestController
@RequestMapping("/catalog")
public class CatalogController {
  private final ICategoryService categoryService;
  private final IApplicationService applicationService;
  private final IUsageProductService productService;

  public CatalogController(
      ICategoryService categoryService,
      IApplicationService applicationService,
      IUsageProductService productService) {
    this.categoryService = categoryService;
    this.applicationService = applicationService;
    this.productService = productService;
  }

  @GetMapping("/categories")
  public Result<List<CategoryDTO>> categories() {
    return categoryService.listCategories();
  }

  @GetMapping("/applications")
  public Result<List<ApplicationSummaryDTO>> applications(@Valid ApplicationSearchForm form) {
    return applicationService.search(form);
  }

  @GetMapping("/applications/{id}")
  public Result<ApplicationDetailDTO> application(@PathVariable @Positive Long id) {
    return applicationService.detail(id);
  }

  @GetMapping("/applications/{id}/products")
  public Result<List<UsageProductDTO>> products(@PathVariable @Positive Long id) {
    return productService.listByApplication(id);
  }

  @GetMapping("/products/{id}")
  public Result<UsageProductDTO> product(@PathVariable @Positive Long id) {
    return productService.detail(id);
  }
}
