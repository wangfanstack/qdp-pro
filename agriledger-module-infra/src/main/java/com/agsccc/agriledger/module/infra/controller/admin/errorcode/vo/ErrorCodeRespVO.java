package com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 错误码文案信息 Response VO")
@Data
public class ErrorCodeRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "错误码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1002000000")
    private Integer code;

    @Schema(description = "模块名", example = "system")
    private String module;

    @Schema(description = "错误提示模板", requiredMode = Schema.RequiredMode.REQUIRED, example = "登录失败，账号密码不正确")
    private String message;

    @Schema(description = "备注", example = "运营要求的营销文案")
    private String memo;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
