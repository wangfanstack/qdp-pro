package com.agsccc.agriledger.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 批量打包下载任务创建 Response VO")
@Data
public class FileZipPrepareRespVO {

    @Schema(description = "下载任务编号（下载中心任务编号，数字字符串，用于轮询进度与拉取 zip）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String downloadId;

}
