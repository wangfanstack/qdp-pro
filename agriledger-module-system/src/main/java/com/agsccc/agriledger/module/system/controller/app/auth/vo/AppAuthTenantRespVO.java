package com.agsccc.agriledger.module.system.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author 农E链源码
 */
@Schema(description = "用户 APP - 账号可访问租户 Response VO")
@Data
public class AppAuthTenantRespVO {

    @Schema(description = "租户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long tenantId;

    @Schema(description = "租户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "农E链源码")
    private String name;

    @Schema(description = "租户状态（0正常 1停用），前端可据此置灰不可切换的租户", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

}
