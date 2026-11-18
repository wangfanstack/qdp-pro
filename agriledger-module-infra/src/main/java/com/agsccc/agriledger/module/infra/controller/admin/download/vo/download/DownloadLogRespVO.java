package com.agsccc.agriledger.module.infra.controller.admin.download.vo.download;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 下载中心审计日志 Response VO")
@Data
public class DownloadLogRespVO {

    @Schema(description = "日志编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long id;

    @Schema(description = "下载任务编号，单文件动作时为空", example = "1024")
    private Long taskId;

    @Schema(description = "文件编号，任务级动作时为空", example = "1")
    private Long fileId;

    @Schema(description = "动作", requiredMode = Schema.RequiredMode.REQUIRED, example = "CREATE_TASK")
    private String action;

    @Schema(description = "客户端 IP", example = "203.0.113.10")
    private String clientIp;

    @Schema(description = "User-Agent", example = "Mozilla/5.0")
    private String userAgent;

    @Schema(description = "操作者用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private String creator;

    @Schema(description = "操作时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-09 10:30:00")
    private LocalDateTime createTime;

}
