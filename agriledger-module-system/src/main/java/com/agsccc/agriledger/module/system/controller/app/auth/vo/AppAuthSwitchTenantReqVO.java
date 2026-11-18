package com.agsccc.agriledger.module.system.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * @author 农E链源码
 */
@Schema(description = "用户 APP - 切换租户 Request VO")
@Data
public class AppAuthSwitchTenantReqVO {

    @Schema(description = "目标租户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "目标租户编号不能为空")
    private Long tenantId;

}
