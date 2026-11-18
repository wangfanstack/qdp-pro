package com.agsccc.agriledger.module.infra.controller.admin.download.vo.download;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 下载中心任务 Response VO")
@Data
public class DownloadTaskRespVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "任务名称，即 zip 文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "公证申请-20260909001.zip")
    private String name;

    @Schema(description = "任务类型：10 - 文件 ZIP 打包", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer type;

    @Schema(description = "任务状态：0 - 排队中、1 - 打包中、10 - 打包成功、20 - 打包失败、30 - 已过期、40 - 已取消", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer status;

    @Schema(description = "待打包文件总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Integer totalCount;

    @Schema(description = "已处理完成文件数（含跳过），进度 = (finishedCount) / totalCount", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer finishedCount;

    @Schema(description = "获取失败的文件数", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer failedCount;

    @Schema(description = "待打包总字节数（进度分母）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1073741824")
    private Long totalBytes;

    @Schema(description = "已写出字节数（进度分子），进度 = completedBytes / totalBytes", requiredMode = Schema.RequiredMode.REQUIRED, example = "536870912")
    private Long completedBytes;

    @Schema(description = "产物大小，单位字节，成功后有值", example = "1073741824")
    private Long fileSize;

    @Schema(description = "失败原因或部分文件跳过提示", example = "2 个文件获取失败已跳过")
    private String errorMessage;

    @Schema(description = "产物过期时间", example = "2026-09-16 10:30:00")
    private LocalDateTime expiredTime;

    @Schema(description = "打包完成时间，成功后有值", example = "2026-09-09 10:35:00")
    private LocalDateTime completedTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-09 10:30:00")
    private LocalDateTime createTime;

}
