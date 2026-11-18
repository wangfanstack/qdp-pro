package com.agsccc.agriledger.module.system.controller.app.auth.vo;

import com.agsccc.agriledger.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

/**
 * @author 农E链源码
 */
@Schema(description = "用户 APP - 发送短信验证码 Request VO")
@Data
public class AppAuthSmsSendReqVO {

    @Schema(description = "手机号（未注册手机号亦可发送，用于新用户注册流程）", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    @NotEmpty(message = "手机号不能为空")
    @Mobile
    private String mobile;

}
