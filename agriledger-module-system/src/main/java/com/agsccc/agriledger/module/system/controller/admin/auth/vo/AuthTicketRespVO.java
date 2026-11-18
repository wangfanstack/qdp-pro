package com.agsccc.agriledger.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author 农E链源码
 */
@Schema(description = "管理后台 - 短信登录（全局账号级，两段式第一段） Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthTicketRespVO {

    @Schema(description = "全局账号编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long accountId;

    @Schema(description = "登录凭证（Auth-Ticket）：通过请求头 Auth-Ticket 携带，用于选择主体（mylist）与进入租户（enter-tenant），"
            + "进入租户时一次性消费换取正式令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticket;

    @Schema(description = "登录凭证过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime ticketExpiresTime;

}
