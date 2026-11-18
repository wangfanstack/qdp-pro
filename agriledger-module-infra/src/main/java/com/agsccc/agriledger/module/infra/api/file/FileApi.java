package com.agsccc.agriledger.module.infra.api.file;

import com.agsccc.agriledger.module.infra.api.file.dto.FileCreateRespDTO;
import com.agsccc.agriledger.module.infra.api.file.dto.FileRespDTO;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;

/**
 * 文件 API 接口
 *
 * @author Sail
 */
public interface FileApi {

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @return 文件路径
     */
    default String createFile(byte[] content) {
        return createFile(content, null, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @return 文件路径
     */
    default String createFile(byte[] content, String name) {
        return createFile(content, name, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content  文件内容
     * @param name     文件名称，允许空
     * @param category 文件分类（video/document/image/attachment/export/temp），允许空：空时按文件类型自动推断
     * @param type     文件的 MIME 类型，允许空
     * @return 文件路径
     */
    String createFile(@NotEmpty(message = "文件内容不能为空") byte[] content, String name, String category, String type);

    /**
     * 保存文件，并返回文件的编号与访问路径（需关联 infra_file 记录时使用）
     *
     * @param content 文件内容
     * @return 文件编号与访问路径
     */
    default FileCreateRespDTO createFileWithInfo(byte[] content) {
        return createFileWithInfo(content, null, null, null);
    }

    /**
     * 保存文件，并返回文件的编号与访问路径（需关联 infra_file 记录时使用）
     *
     * @param content 文件内容
     * @param name    文件名称，允许空
     * @return 文件编号与访问路径
     */
    default FileCreateRespDTO createFileWithInfo(byte[] content, String name) {
        return createFileWithInfo(content, name, null, null);
    }

    /**
     * 保存文件，并返回文件的编号与访问路径（需关联 infra_file 记录时使用）
     *
     * @param content  文件内容
     * @param name     文件名称，允许空
     * @param category 文件分类（video/document/image/attachment/export/temp），允许空：空时按文件类型自动推断
     * @param type     文件的 MIME 类型，允许空
     * @return 文件编号与访问路径
     */
    FileCreateRespDTO createFileWithInfo(@NotEmpty(message = "文件内容不能为空") byte[] content, String name, String category, String type);

    /**
     * 登记外部文件 URL 到 infra_file（不搬运文件内容，文件本体在外部存储）
     *
     * <p>适用于文件已落在外部存储（如 TRTC 云端录制的 COS 桶）、仅需纳入统一文件管理的场景
     *
     * @param name 文件名称
     * @param url  完整的文件访问地址
     * @return 文件编号
     */
    Long createFileRecord(@NotEmpty(message = "文件名不能为空") String name, @NotEmpty(message = "URL 不能为空") String url);

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    String presignGetUrl(@NotEmpty(message = "URL 不能为空") String url, Integer expirationSeconds);

    /**
     * 按编号查询文件（存在性校验/轻量展示信息用）
     *
     * @param id 文件编号（infra_file.id）
     * @return 文件信息；不存在为 null
     */
    FileRespDTO getFile(@NotNull(message = "文件编号不能为空") Long id);

    /**
     * 校验文件存在：不存在抛 FILE_NOT_EXISTS。
     * 供跨模块绑定 infra_file.id 前（如公证材料 notary_material.file_id）做存在性校验，
     * 把悬空引用挡在写入之前，而不是延迟到详情展示/下载时才暴露
     *
     * @param id 文件编号（infra_file.id）
     */
    default void validateFileExists(Long id) {
        if (getFile(id) == null) {
            throw exception(FILE_NOT_EXISTS);
        }
    }

}
