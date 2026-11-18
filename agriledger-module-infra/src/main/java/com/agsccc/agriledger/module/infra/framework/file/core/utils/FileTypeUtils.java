package com.agsccc.agriledger.module.infra.framework.file.core.utils;

import com.agsccc.agriledger.framework.common.util.http.HttpUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件类型 Utils
 *
 * @author Sail
 */
@Slf4j
public class FileTypeUtils {

    private static final Tika TIKA = new Tika();

    /**
     * 获得文件的 mineType，对于 doc，jar 等文件会有误差
     *
     * @param data 文件内容
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    @SneakyThrows
    public static String getMineType(byte[] data) {
        return TIKA.detect(data);
    }

    /**
     * 已知文件名，获取文件类型，在某些情况下比通过字节数组准确，例如使用 jar 文件时，通过名字更为准确
     *
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(String name) {
        return TIKA.detect(name);
    }

    /**
     * 在拥有文件和数据的情况下，最好使用此方法，最为准确
     *
     * @param data 文件内容
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(byte[] data, String name) {
        return TIKA.detect(data, name);
    }

    /**
     * 基于流式内容的类型探测，配合大文件流式上传使用，避免整个文件读入内存
     *
     * 注意：Tika 仅读取流头部少量字节做 magic number 识别，不关闭传入的流，
     * 探测用的流应独立打开、读取后直接关闭，避免影响后续上传内容
     *
     * @param data 文件流
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    @SneakyThrows
    public static String getMineType(InputStream data, String name) {
        return TIKA.detect(data, name);
    }

    /**
     * 根据 mineType 获得文件后缀
     *
     * 注意：如果获取不到，或者发生异常，都返回 null
     *
     * @param mineType 类型
     * @return 后缀，例如说 .pdf
     */
    public static String getExtension(String mineType) {
        try {
            return MimeTypes.getDefaultMimeTypes().forName(mineType).getExtension();
        } catch (MimeTypeException e) {
            log.warn("[getExtension][获取文件后缀({}) 失败]", mineType, e);
            return null;
        }
    }

    /**
     * 返回附件
     *
     * @param response 响应
     * @param filename 文件名
     * @param content  附件内容
     */
    public static void writeAttachment(HttpServletResponse response, String filename, byte[] content) throws IOException {
        // 设置 header 和 contentType
        String mineType = getMineType(content, filename);
        response.setContentType(mineType);
        // 设置内容显示、下载文件名：https://www.cnblogs.com/wq-9/articles/12165056.html
        if (isImage(mineType)) {
            // 参见 https://github.com/YunaiV/ruoyi-vue-pro/issues/692 讨论
            response.setHeader("Content-Disposition", buildContentDisposition("inline", filename));
        } else {
            response.setHeader("Content-Disposition", buildContentDisposition("attachment", filename));
        }
        // 针对 video 的特殊处理，解决视频地址在移动端播放的兼容性问题
        if (StringUtils.containsIgnoreCase(mineType, "video")) {
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(content.length));
        }
        // 输出附件
        StreamUtils.copy(content, response.getOutputStream());
    }

    public static String buildContentDisposition(String dispositionType, String filename) {
        return String.format("%s;filename=\"%s\";filename*=UTF-8''%s",
                dispositionType, buildFallbackFilename(filename), HttpUtils.encodeUrlPathSegment(filename));
    }

    /**
     * 流式附件下载的响应头设置（内容体由调用方流式写出，配合 Content-Length 已知的场景，
     * 前端可展示下载进度条）
     *
     * @param response      响应
     * @param filename      下载文件名
     * @param contentType   内容类型，例如 "application/zip"
     * @param contentLength 内容大小，单位字节，允许空（未知时不设置）
     */
    public static void writeAttachmentHeaders(HttpServletResponse response, String filename,
                                              String contentType, Long contentLength) {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", buildContentDisposition("attachment", filename));
        if (contentLength != null) {
            response.setContentLengthLong(contentLength);
        }
    }

    private static String buildFallbackFilename(String filename) {
        if (StringUtils.isEmpty(filename)) {
            return "download";
        }
        StringBuilder result = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char ch = filename.charAt(i);
            if (ch == '"' || ch == '\\') {
                result.append('\\').append(ch);
            } else if (ch >= 0x20 && ch <= 0x7E) {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }

    /**
     * 判断是否是图片
     *
     * @param mineType 类型
     * @return 是否是图片
     */
    public static boolean isImage(String mineType) {
        return StringUtils.startsWith(mineType, "image/");
    }

}
