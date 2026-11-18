package com.agsccc.agriledger.module.infra.controller.admin.file;

import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.*;
import com.agsccc.agriledger.module.infra.convert.file.FileConvert;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.agsccc.agriledger.module.infra.service.download.DownloadLogService;
import com.agsccc.agriledger.module.infra.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;
import static com.agsccc.agriledger.module.infra.enums.download.DownloadLogActionEnum.GET_FILE_URL;

@Tag(name = "管理后台 - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
@Slf4j
public class FileController {

    @Resource
    private FileService fileService;

    @Resource
    private DownloadLogService downloadLogService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "模式一：后端上传文件")
    @Parameter(name = "file", description = "文件附件", required = true, schema = @Schema(type = "string", format = "binary"))
    public CommonResult<Map<String, Object>> uploadFile(FileUploadReqVO uploadReqVO) {
        MultipartFile file = uploadReqVO.getFile();
        // 流式上传：Tomcat 已将请求体落盘为临时文件，通过可重复开流的 provider 透传，避免整个文件读入内存
        return success(fileService.createFile(file::getInputStream, file.getSize(),
                file.getOriginalFilename(), uploadReqVO.getCategory(), file.getContentType()));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "获取文件预签名地址（上传）", description = "模式二：前端上传文件：用于前端直接上传七牛、阿里云 OSS 等文件存储器")
    @Parameters({@Parameter(name = "name", description = "文件名称", required = true),
            @Parameter(name = "category", description = "文件分类，允许空：空时按文件类型自动归类")})
    public CommonResult<FilePresignedUrlRespVO> getFilePresignedUrl(@RequestParam("name") String name, @RequestParam(value = "category", required = false) String category) {
        return success(fileService.presignPutUrl(name, category));
    }

    @PostMapping("/create")
    @Operation(summary = "创建文件", description = "模式二：前端上传文件：配合 presigned-url 接口，记录上传了上传的文件")
    public CommonResult<Long> createFile(@Valid @RequestBody FileCreateReqVO createReqVO) {
        return success(fileService.createFile(createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得文件")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<FileRespVO> getFile(@RequestParam("id") Long id) {
        return success(FileConvert.INSTANCE.convert(fileService.getFile(id)));
    }

    @GetMapping("/get-download-url")
    @Operation(summary = "获得单文件下载直链", description = "S3 等支持预签名的存储返回带附件名的临时直链（浏览器直连对象存储，支持 Range 断点续传），"
            + "其余存储返回文件既有访问地址")
    @Parameter(name = "id", description = "文件编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<FileDownloadUrlRespVO> getFileDownloadUrl(@RequestParam("id") Long id, HttpServletRequest request) {
        FileDownloadUrlRespVO respVO = fileService.getFileDownloadUrl(id);
        downloadLogService.record(GET_FILE_URL, null, id, request);
        return success(respVO);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件")
    @Parameter(name = "id", description = "编号", required = true)
    public CommonResult<Boolean> deleteFile(@RequestParam("id") Long id) throws Exception {
        fileService.deleteFile(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除文件")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> deleteFileList(@RequestParam("ids") List<Long> ids) throws Exception {
        fileService.deleteFileList(ids);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得文件分页")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<PageResult<FileRespVO>> getFilePage(@Valid FilePageReqVO pageVO) {
        PageResult<FileDO> pageResult = fileService.getFilePage(pageVO);
        return success(FileConvert.INSTANCE.convertPage(pageResult));
    }

}
