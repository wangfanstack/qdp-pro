package com.agsccc.agriledger.module.infra.controller.admin.download;

import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadLogPageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadLogRespVO;
import com.agsccc.agriledger.module.infra.convert.download.DownloadLogConvert;
import com.agsccc.agriledger.module.infra.service.download.DownloadLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;

/**
 * 下载中心审计日志查询
 *
 * <p>记录任务创建/取消/删除、获取下载直链、代理下载、单文件获取直链等动作，
 * 满足合规追溯需求（谁在何时从哪个 IP 发起了下载）。
 * 权限沿用文件模块的 infra:file:query，无需新增菜单配置。
 *
 * @author Sail
 */
@Tag(name = "管理后台 - 下载中心审计日志")
@RestController
@RequestMapping("/infra/download-log")
@Validated
public class DownloadLogController {

    @Resource
    private DownloadLogService downloadLogService;

    @GetMapping("/page")
    @Operation(summary = "获得下载审计日志分页")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<PageResult<DownloadLogRespVO>> getDownloadLogPage(@Valid DownloadLogPageReqVO pageReqVO) {
        return success(DownloadLogConvert.INSTANCE.convertPage(downloadLogService.getDownloadLogPage(pageReqVO)));
    }

}
