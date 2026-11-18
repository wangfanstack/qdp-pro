package com.agsccc.agriledger.module.system.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author 农E链源码
 */
@Schema(description = "用户 APP - 短信登录（全局账号级，两段式第一段） Result VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthPreLoginRespVO {

    @Schema(description = "全局账号编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long accountId;

    @Schema(description = "登录凭证（Ticket）：用于进入商户前的自助操作（选商户进入/注册/查进度/取消/重提），"
            + "通过请求头 Ticket 携带；进入商户时一次性消费换取正式令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticket;

    @Schema(description = "登录凭证过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime ticketExpiresTime;

}
