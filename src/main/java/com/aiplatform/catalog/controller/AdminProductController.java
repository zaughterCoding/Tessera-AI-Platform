package com.aiplatform.catalog.controller;

import com.aiplatform.catalog.dto.ProductAdminUpdateForm;
import com.aiplatform.catalog.service.IUsageProductService;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.operations.audit.AuditOperation;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品元数据管理写入口；与目录失效任务同事务提交。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/catalog/products")
public class AdminProductController {
  private final IUsageProductService products;

  public AdminProductController(IUsageProductService products) {
    this.products = products;
  }

  @PutMapping("/{id}")
  @AuditOperation(action = "catalog.product.update", objectType = "product")
  public Result<Void> update(
      @PathVariable @Positive Long id,
      @Valid @RequestBody ProductAdminUpdateForm form) {
    UserDTO actor = UserHolder.getUser();
    products.updateProduct(id, form, actor == null ? null : actor.getId());
    return Result.ok();
  }
}
