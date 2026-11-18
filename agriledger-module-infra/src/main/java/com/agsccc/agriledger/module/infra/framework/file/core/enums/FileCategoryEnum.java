package com.agsccc.agriledger.module.infra.framework.file.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Set;

/**
 * 文件分类枚举
 *
 * <p>决定文件在存储桶中的顶层目录，存储路径规范：{@code {category}/{tenantId}/{yyyy}/{MM}/{uuid}.{ext}}
 *
 * <p>分类之间扩展名存在重叠（xlsx 同属 document 与 export、zip 同属 attachment 与 export），
 * 仅靠扩展名无法唯一区分：export/temp 由系统代码生成时显式指定，
 * 用户上传场景按 MIME/扩展名自动推断（见 {@link #infer}），无法识别时兜底 attachment
 *
 * @author Sail
 */
@AllArgsConstructor
@Getter
public enum FileCategoryEnum {

    /**
     * 视频文件
     */
    VIDEO("video"),

    /**
     * 正式业务文档
     */
    DOCUMENT("document"),

    /**
     * 正式业务图片
     */
    IMAGE("image"),

    /**
     * 用户上传的普通附件（自动推断的兜底分类）
     */
    ATTACHMENT("attachment"),

    /**
     * 系统生成的导出文件（下载中心打包产物等）
     */
    EXPORT("export"),

    /**
     * 临时文件
     */
    TEMP("temp"),

    ;

    /**
     * 分类目录段
     */
    private final String directory;

    /**
     * document 分类的 MIME 集合：pdf、office 文档、纯文本
     */
    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain");

    /**
     * video 分类的扩展名集合：MIME 缺失或泛化（octet-stream）时的补充判定
     */
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "m4v", "webm", "mpeg", "mpg", "3gp");

    /**
     * document 分类的扩展名集合
     */
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "ofd");

    /**
     * image 分类的扩展名集合
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic");

    /**
     * 根据分类字符串解析枚举
     *
     * @param category 分类字符串，允许空
     * @return 枚举值，未匹配返回 null
     */
    public static FileCategoryEnum of(String category) {
        if (StringUtils.isEmpty(category)) {
            return null;
        }
        for (FileCategoryEnum value : values()) {
            if (value.getDirectory().equals(category)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 校验分类字符串是否合法（空视为合法，走自动推断）
     *
     * @param category 分类字符串
     * @return 是否合法
     */
    public static boolean isValid(String category) {
        return StringUtils.isEmpty(category) || of(category) != null;
    }

    /**
     * 按文件名与 MIME 类型自动推断分类
     *
     * <p>MIME 前缀优先（video/、image/），document 按 MIME 精确集合判定；
     * MIME 缺失或泛化时退化为扩展名判定；均未命中兜底 attachment
     *
     * @param name 文件名，允许空
     * @param type MIME 类型，允许空
     * @return 推断出的分类
     */
    public static FileCategoryEnum infer(String name, String type) {
        // 规范化 MIME（小写、去空白，空安全），Set.of 的 contains 不接受 null
        String normalizedType = StringUtils.lowerCase(StringUtils.trimToEmpty(type));
        if (Strings.CS.startsWith(normalizedType, "video/")) {
            return VIDEO;
        }
        if (Strings.CS.startsWith(normalizedType, "image/")) {
            return IMAGE;
        }
        if (DOCUMENT_MIME_TYPES.contains(normalizedType)) {
            return DOCUMENT;
        }
        String extension = extensionOf(name);
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return VIDEO;
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return DOCUMENT;
        }
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return IMAGE;
        }
        return ATTACHMENT;
    }

    /**
     * 提取文件扩展名（小写、不含点），无扩展名返回空串
     */
    private static String extensionOf(String name) {
        return StringUtils.lowerCase(StringUtils.defaultString(StringUtils.substringAfterLast(name, ".")));
    }

}
