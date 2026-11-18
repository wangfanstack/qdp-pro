package com.agsccc.agriledger.module.system.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;

/**
 * @author 农E链源码
 */
@Schema(description = "用户 APP - 刷新访问令牌 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthRefreshTokenReqVO {

    @Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED, example = "nice")
    @NotEmpty(message = "刷新令牌不能为空")
    private String refreshToken;

}
