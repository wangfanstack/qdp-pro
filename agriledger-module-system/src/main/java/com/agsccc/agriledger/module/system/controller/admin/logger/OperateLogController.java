package com.agsccc.agriledger.module.system.controller.admin.logger;

import com.agsccc.agriledger.framework.apilog.core.annotation.ApiAccessLog;
import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.pojo.PageParam;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.excel.core.util.ExcelUtils;
import com.agsccc.agriledger.framework.translate.core.TranslateUtils;
import com.agsccc.agriledger.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO;
import com.agsccc.agriledger.module.system.controller.admin.logger.vo.operatelog.OperateLogRespVO;
import com.agsccc.agriledger.module.system.convert.logger.OperateLogConvert;
import com.agsccc.agriledger.module.system.dal.dataobject.logger.OperateLogDO;
import com.agsccc.agriledger.module.system.service.logger.OperateLogService;
import org.dromara.core.trans.anno.TransMethodResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static com.agsccc.agriledger.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 操作日志")
@RestController
@RequestMapping("/system/operate-log")
@Validated
public class OperateLogController {

    @Resource
    private OperateLogService operateLogService;

    @GetMapping("/get")
    @Operation(summary = "查看操作日志")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('system:operate-log:query')")
    public CommonResult<OperateLogRespVO> getOperateLog(@RequestParam("id") Long id) {
        OperateLogDO operateLog = operateLogService.getOperateLog(id);
        return success(OperateLogConvert.INSTANCE.convertToRespVO(operateLog));
    }

    @GetMapping("/page")
    @Operation(summary = "查看操作日志分页列表")
    @PreAuthorize("@ss.hasPermission('system:operate-log:query')")
    @TransMethodResult
    public CommonResult<PageResult<OperateLogRespVO>> pageOperateLog(@Valid OperateLogPageReqVO pageReqVO) {
        PageResult<OperateLogDO> pageResult = operateLogService.getOperateLogPage(pageReqVO);
        return success(OperateLogConvert.INSTANCE.convertRespVOPage(pageResult));
    }

    @Operation(summary = "导出操作日志")
    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:operate-log:export')")
    @TransMethodResult
    @ApiAccessLog(operateType = EXPORT)
    public void exportOperateLog(HttpServletResponse response, @Valid OperateLogPageReqVO exportReqVO) throws IOException {
        exportReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<OperateLogDO> list = operateLogService.getOperateLogPage(exportReqVO).getList();
        ExcelUtils.write(response, "操作日志.xls", "数据列表", OperateLogRespVO.class,
                TranslateUtils.translate(OperateLogConvert.INSTANCE.convertRespVOList(list)));
    }

}
