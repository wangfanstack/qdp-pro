package com.agsccc.agriledger.module.infra.controller.app.file;

import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import com.agsccc.agriledger.module.infra.controller.app.file.vo.AppFileUploadReqVO;
import com.agsccc.agriledger.module.infra.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
public class AppFileController {

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @Schema(type = "string", format = "binary"))
    @PermitAll
    public CommonResult<Map<String, Object>> uploadFile(@Valid AppFileUploadReqVO uploadReqVO) throws Exception {
        MultipartFile file = uploadReqVO.getFile();
        // 流式上传：Tomcat 已将请求体落盘为临时文件，通过可重复开流的 provider 透传，避免整个文件读入内存
        return success(fileService.createFile(file::getInputStream, file.getSize(),
                file.getOriginalFilename(), uploadReqVO.getCategory(), file.getContentType()));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "获取文件预签名地址（上传）", description = "模式二：前端上传文件：用于前端直接上传七牛、阿里云 OSS 等文件存储器")
    @Parameters({
            @Parameter(name = "name", description = "文件名称", required = true),
            @Parameter(name = "category", description = "文件分类，允许空：空时按文件类型自动归类")
    })
    public CommonResult<FilePresignedUrlRespVO> getFilePresignedUrl(
            @RequestParam("name") String name,
            @RequestParam(value = "category", required = false) String category) {
        return success(fileService.presignPutUrl(name, category));
    }

    @PostMapping("/create")
    @Operation(summary = "创建文件", description = "模式二：前端上传文件：配合 presigned-url 接口，记录上传了上传的文件")
    @PermitAll
    public CommonResult<Long> createFile(@Valid @RequestBody FileCreateReqVO createReqVO) {
        return success(fileService.createFile(createReqVO));
    }

}
