package com.agsccc.agriledger.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 批量打包下载任务进度 Response VO")
@Data
public class FileZipProgressRespVO {

    @Schema(description = "任务状态（PROCESSING-打包中 SUCCESS-完成可下载 FAILED-失败，已过期归并为 FAILED）", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROCESSING")
    private String status;

    @Schema(description = "待打包文件总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Integer total;

    @Schema(description = "已处理完成的条目数（含获取失败被跳过的）", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer finished;

    @Schema(description = "失败原因摘要（FAILED 时返回）", example = "临时文件写入失败")
    private String error;

}
