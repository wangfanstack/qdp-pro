package com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo;

import com.agsccc.agriledger.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 错误码文案分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ErrorCodePageReqVO extends PageParam {

    @Schema(description = "错误码", example = "1002000000")
    private Integer code;

    @Schema(description = "模块名，模糊匹配", example = "system")
    private String module;

    @Schema(description = "错误提示，模糊匹配", example = "登录失败")
    private String message;

}
