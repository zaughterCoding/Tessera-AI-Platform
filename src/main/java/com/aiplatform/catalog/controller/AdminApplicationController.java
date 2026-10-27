package com.aiplatform.catalog.controller;

import com.aiplatform.catalog.dto.ApplicationAdminUpdateForm;
import com.aiplatform.catalog.service.IApplicationService;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.RequireAdmin;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应用元数据管理写入口；缓存失效由同事务任务异步完成。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/catalog/applications")
public class AdminApplicationController {
  private final IApplicationService applications;

  public AdminApplicationController(IApplicationService applications) {
    this.applications = applications;
  }

  @PutMapping("/{id}")
  public Result<Void> update(
      @PathVariable @Positive Long id,
      @Valid @RequestBody ApplicationAdminUpdateForm form) {
    applications.updateMetadata(id, form);
    return Result.ok();
  }
}
