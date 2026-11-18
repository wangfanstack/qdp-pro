package com.agsccc.agriledger.module.infra.controller.admin.download.vo.download;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 下载中心创建任务 Request VO")
@Data
public class DownloadTaskCreateReqVO {

    @Schema(description = "任务类型，参见 DownloadTaskTypeEnum，不传默认为文件 ZIP 打包", example = "10")
    private Integer type;

    @Schema(description = "文件编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1, 2, 3]")
    @NotEmpty(message = "文件编号列表不能为空")
    private List<Long> fileIds;

    @Schema(description = "幂等请求标识，同一用户重复提交相同标识时返回原任务，防连续点击产出多个包", example = "0f9d3a2b-7c41-4e8a-9b6d-1e2f3a4b5c6d")
    private String requestId;

    @Schema(description = "任务名称（zip 文件名），允许空，默认按时间生成", example = "公证申请-20260909001.zip")
    private String name;

}
