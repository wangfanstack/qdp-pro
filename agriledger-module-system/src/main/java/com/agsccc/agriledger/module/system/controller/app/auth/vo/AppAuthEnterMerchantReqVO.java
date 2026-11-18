package com.agsccc.agriledger.module.system.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * @author 农E链源码
 */
@Schema(description = "用户 APP - 进入商户（两段式第二段，凭 Ticket 换正式令牌） Request VO")
@Data
public class AppAuthEnterMerchantReqVO {

    @Schema(description = "目标租户编号（从 mylist 的主体项获得）", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    @NotNull(message = "目标租户编号不能为空")
    private Long tenantId;

}
