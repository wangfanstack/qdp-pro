package com.agsccc.agriledger.module.infra.controller.admin.download;

import com.agsccc.agriledger.framework.apilog.core.annotation.ApiAccessLog;
import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.idempotent.core.annotation.Idempotent;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskDownloadUrlRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskPageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskRespVO;
import com.agsccc.agriledger.module.infra.convert.download.DownloadTaskConvert;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import com.agsccc.agriledger.module.infra.service.download.DownloadLogService;
import com.agsccc.agriledger.module.infra.service.download.DownloadTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.agsccc.agriledger.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;
import static com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static com.agsccc.agriledger.module.infra.enums.download.DownloadLogActionEnum.*;

/**
 * 下载中心：多文件、大文件异步打包 ZIP 的任务管理
 *
 * <p>任务创建即落库（排队态）并投递执行消息，打包由消费者异步执行，
 * 任务列表与进度均持久化，刷新页面或服务重启不丢失。
 * 权限沿用文件模块的 infra:file:query，与既有批量打包接口一致，无需新增菜单配置。
 *
 * @author Sail
 */
@Tag(name = "管理后台 - 下载中心")
@RestController
@RequestMapping("/infra/download-task")
@Validated
public class DownloadTaskController {

    @Resource
    private DownloadTaskService downloadTaskService;
    @Resource
    private DownloadLogService downloadLogService;

    @PostMapping("/create")
    @Operation(summary = "创建下载任务", description = "批量文件打包 ZIP：创建即排队（消息驱动异步打包），轮询 get 接口查进度，"
            + "打包成功后获取下载直链或直接下载；请求体可携带 requestId 实现幂等，同一用户重复提交相同标识时返回原任务")
    @Idempotent
    public CommonResult<Long> createDownloadTask(@Valid @RequestBody DownloadTaskCreateReqVO createReqVO, HttpServletRequest request) {
        Long taskId = downloadTaskService.createDownloadTask(getLoginUserId(), createReqVO);
        downloadLogService.record(CREATE_TASK, taskId, null, request);
        return success(taskId);
    }

    @GetMapping("/page")
    @Operation(summary = "获得下载任务分页", description = "仅返回当前用户自己创建的任务；任务持久化，刷新页面不丢失")
    public CommonResult<PageResult<DownloadTaskRespVO>> getDownloadTaskPage(@Valid DownloadTaskPageReqVO pageReqVO) {
        PageResult<DownloadTaskDO> pageResult = downloadTaskService.getDownloadTaskPage(getLoginUserId(), pageReqVO);
        return success(DownloadTaskConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/count")
    @Operation(summary = "统计打包中任务数", description = "返回当前用户处于打包中（PROCESSING）的任务数量，用于前端角标/轮询提示")
    public CommonResult<Long> getProcessingDownloadTaskCount() {
        return success(downloadTaskService.getProcessingDownloadTaskCount(getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得下载任务", description = "打包中可作为进度轮询接口，返回状态与文件数/字节双维度进度")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    public CommonResult<DownloadTaskRespVO> getDownloadTask(@RequestParam("id") Long id) {
        return success(DownloadTaskConvert.INSTANCE.convert(downloadTaskService.getDownloadTask(getLoginUserId(), id)));
    }

    @GetMapping("/download-url")
    @Operation(summary = "获取任务产物下载直链", description = "S3 等支持预签名的存储返回带附件名的临时直链（浏览器直连对象存储，支持 Range 断点续传），"
            + "其余存储返回服务端代理下载相对路径")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    @ApiAccessLog(operateType = EXPORT)
    public CommonResult<DownloadTaskDownloadUrlRespVO> getDownloadTaskUrl(@RequestParam("id") Long id, HttpServletRequest request) {
        DownloadTaskDownloadUrlRespVO respVO = downloadTaskService.getTaskDownloadUrl(getLoginUserId(), id);
        downloadLogService.record(GET_TASK_URL, id, null, request);
        return success(respVO);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消下载任务", description = "排队中直接取消；打包中由执行线程在下一个进度检查点协作中止（大文件每 32MB 一个检查点）")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    public CommonResult<Boolean> cancelDownloadTask(@RequestParam("id") Long id, HttpServletRequest request) {
        downloadTaskService.cancelDownloadTask(getLoginUserId(), id);
        downloadLogService.record(CANCEL, id, null, request);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除下载任务")
    @Parameter(name = "id", description = "任务编号", required = true, example = "1024")
    public CommonResult<Boolean> deleteDownloadTask(@RequestParam("id") Long id, HttpServletRequest request) throws Exception {
        downloadTaskService.deleteDownloadTask(getLoginUserId(), id);
        downloadLogService.record(DELETE, id, null, request);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除下载任务")
    @Parameter(name = "ids", description = "任务编号列表", required = true, example = "[1024, 1025]")
    public CommonResult<Boolean> deleteDownloadTaskList(@RequestParam("ids") List<Long> ids, HttpServletRequest request) throws Exception {
        downloadTaskService.deleteDownloadTaskList(getLoginUserId(), ids);
        for (Long id : ids) {
            downloadLogService.record(DELETE, id, null, request);
        }
        return success(true);
    }

}
