package com.agsccc.agriledger.module.infra.controller.admin.download.vo.download;

import com.agsccc.agriledger.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static com.agsccc.agriledger.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 下载中心任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DownloadTaskPageReqVO extends PageParam {

    @Schema(description = "任务状态：0 - 排队中、1 - 打包中、10 - 打包成功、20 - 打包失败、30 - 已过期、40 - 已取消", example = "10")
    private Integer status;

    @Schema(description = "创建时间", example = "[2026-09-01 00:00:00, 2026-09-09 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
