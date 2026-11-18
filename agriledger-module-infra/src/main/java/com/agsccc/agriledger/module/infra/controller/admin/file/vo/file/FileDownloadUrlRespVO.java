package com.agsccc.agriledger.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 单文件下载直链 Response VO")
@Data
public class FileDownloadUrlRespVO {

    @Schema(description = "文件编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long fileId;

    @Schema(description = "文件名（下载时展示与保存名）", requiredMode = Schema.RequiredMode.REQUIRED, example = "合同.pdf")
    private String fileName;

    @Schema(description = "下载地址：支持预签名的存储（S3 等）返回带附件名的临时直链，浏览器直连对象存储下载并支持 Range 断点续传；"
            + "其余存储返回文件既有访问地址（服务端代理）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "https://bucket.cos.ap-shanghai.myqcloud.com/xxx.pdf?X-Amz-Signature=...")
    private String downloadUrl;

    @Schema(description = "直链有效期，单位秒；代理下载地址无有效期，返回空", example = "600")
    private Integer expireSeconds;

}
