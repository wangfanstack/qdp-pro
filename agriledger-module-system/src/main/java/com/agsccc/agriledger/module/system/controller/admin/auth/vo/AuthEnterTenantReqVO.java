package com.agsccc.agriledger.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * @author 农E链源码
 */
@Schema(description = "管理后台 - 进入租户（两段式第二段，凭 Auth-Ticket 换 Web 正式令牌） Request VO")
@Data
public class AuthEnterTenantReqVO {

    @Schema(description = "目标租户编号（从 mylist 的主体项获得）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "目标租户编号不能为空")
    private Long tenantId;

}
