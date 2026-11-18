package com.agsccc.agriledger.module.infra.controller.admin.errorcode;

import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodePageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodeRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodeSaveReqVO;
import com.agsccc.agriledger.module.infra.convert.errorcode.ErrorCodeConvert;
import com.agsccc.agriledger.module.infra.dal.dataobject.errorcode.ErrorCodeDO;
import com.agsccc.agriledger.module.infra.service.errorcode.ErrorCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 错误码管理")
@RestController
@RequestMapping("/infra/error-code")
@Validated
public class ErrorCodeController {

    @Resource
    private ErrorCodeService errorCodeService;

    @PostMapping("/create")
    @Operation(summary = "创建错误码文案")
    @PreAuthorize("@ss.hasPermission('infra:error-code:create')")
    public CommonResult<Long> createErrorCode(@Valid @RequestBody ErrorCodeSaveReqVO createReqVO) {
        return success(errorCodeService.createErrorCode(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新错误码文案", description = "修改后本节点立即生效，集群其他节点约 1 分钟内生效，无需发版")
    @PreAuthorize("@ss.hasPermission('infra:error-code:update')")
    public CommonResult<Boolean> updateErrorCode(@Valid @RequestBody ErrorCodeSaveReqVO updateReqVO) {
        errorCodeService.updateErrorCode(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除错误码文案", description = "删除后恢复使用代码中的默认文案")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('infra:error-code:delete')")
    public CommonResult<Boolean> deleteErrorCode(@RequestParam("id") Long id) {
        errorCodeService.deleteErrorCode(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得错误码文案")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('infra:error-code:query')")
    public CommonResult<ErrorCodeRespVO> getErrorCode(@RequestParam("id") Long id) {
        ErrorCodeDO errorCode = errorCodeService.getErrorCode(id);
        return success(ErrorCodeConvert.INSTANCE.convert(errorCode));
    }

    @GetMapping("/page")
    @Operation(summary = "获得错误码文案分页")
    @PreAuthorize("@ss.hasPermission('infra:error-code:query')")
    public CommonResult<PageResult<ErrorCodeRespVO>> getErrorCodePage(@Valid ErrorCodePageReqVO pageReqVO) {
        PageResult<ErrorCodeDO> page = errorCodeService.getErrorCodePage(pageReqVO);
        return success(ErrorCodeConvert.INSTANCE.convertPage(page));
    }

}
