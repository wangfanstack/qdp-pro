package com.agsccc.agriledger.module.infra.controller.admin.download.vo.download;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 下载中心产物下载直链 Response VO")
@Data
public class DownloadTaskDownloadUrlRespVO {

    @Schema(description = "下载地址：支持预签名的存储（S3 等）返回带附件名的临时直链，浏览器直连对象存储下载并支持 Range 断点续传；"
            + "其余存储返回服务端代理下载相对路径（infra/download-task/download?id=），前端拼接网关前缀访问",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "https://bucket.cos.ap-shanghai.myqcloud.com/download-center/xxx.zip?X-Amz-Signature=...")
    private String downloadUrl;

    @Schema(description = "直链有效期，单位秒；代理下载地址无有效期，返回空", example = "600")
    private Integer expireSeconds;

}
