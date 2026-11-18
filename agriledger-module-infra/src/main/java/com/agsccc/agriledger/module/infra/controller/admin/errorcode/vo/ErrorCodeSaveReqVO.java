package com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 错误码文案创建/修改 Request VO")
@Data
public class ErrorCodeSaveReqVO {

    @Schema(description = "编号，修改时必传", example = "1")
    private Long id;

    @Schema(description = "错误码，与代码中 ErrorCodeConstants 定义的编码一致", requiredMode = Schema.RequiredMode.REQUIRED, example = "1002000000")
    @NotNull(message = "错误码不能为空")
    private Integer code;

    @Schema(description = "模块名，标识错误码的业务归属", example = "system")
    @Size(max = 64, message = "模块名不能超过 64 个字符")
    private String module;

    @Schema(description = "错误提示模板，支持 {} 占位符，占位符个数需与代码抛出时传参一致", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "错误提示不能为空")
    @Size(max = 512, message = "错误提示不能超过 512 个字符")
    private String message;

    @Schema(description = "备注", example = "运营要求的营销文案")
    @Size(max = 512, message = "备注不能超过 512 个字符")
    private String memo;

}
