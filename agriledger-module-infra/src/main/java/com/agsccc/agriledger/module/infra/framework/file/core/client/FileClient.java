package com.agsccc.agriledger.module.infra.framework.file.core.client;

import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 文件客户端
 *
 * @author Sail
 */
public interface FileClient {

    /**
     * 获得客户端编号
     *
     * @return 客户端编号
     */
    Long getId();

    /**
     * 上传文件
     *
     * @param content 文件内容
     * @param path    相对路径
     * @return 完整路径，即 HTTP 访问地址
     * @throws Exception 上传文件时，抛出 Exception 异常
     */
    String upload(byte[] content, String path, String type) throws Exception;

    /**
     * 流式上传文件，配合大文件上传使用，避免整个文件读入内存
     *
     * 未覆写的实现（如 DB 存储，写入 BLOB 天然需要完整字节）降级读取到内存，
     * 行为与 {@link #upload(byte[], String, String)} 一致
     *
     * @param content 内容流提供者，支持重复打开（S3 签名或重试时需要重新读取）
     * @param size    文件大小，部分存储（如 S3）要求精确的 contentLength
     * @param path    相对路径
     * @param type    文件的 MIME 类型
     * @return 完整路径，即 HTTP 访问地址
     * @throws Exception 上传文件时，抛出 Exception 异常
     */
    default String upload(InputStreamProvider content, long size, String path, String type) throws Exception {
        try (InputStream in = content.openStream()) {
            return upload(StreamUtils.copyToByteArray(in), path, type);
        }
    }

    /**
     * 删除文件
     *
     * @param path 相对路径
     * @throws Exception 删除文件时，抛出 Exception 异常
     */
    void delete(String path) throws Exception;

    /**
     * 获得文件的内容
     *
     * @param path 相对路径
     * @return 文件的内容
     */
    byte[] getContent(String path) throws Exception;

    /**
     * 获得文件内容的输入流，配合大文件下载使用，避免整个文件读入内存。
     * 返回的流由调用方负责关闭
     *
     * 未覆写的实现（如 DB 存储 BLOB 天然整块读取）降级读取到内存，
     * 行为与 {@link #getContent(String)} 一致，内容不存在时返回 null
     *
     * @param path 相对路径
     * @return 内容输入流，存储中不存在时返回 null
     */
    default InputStream getContentStream(String path) throws Exception {
        byte[] content = getContent(path);
        return content != null ? new ByteArrayInputStream(content) : null;
    }

    // ========== 文件签名，目前仅 S3 支持 ==========

    /**
     * 获得文件预签名地址，用于上传
     *
     * @param path 相对路径
     * @return 文件预签名地址
     */
    default String presignPutUrl(String path) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    default String presignGetUrl(String url, Integer expirationSeconds) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 生成带附件文件名的预签名下载地址，配合大文件下载使用：
     * 浏览器直连对象存储下载（天然支持 Range 断点续传），文件流量不经过应用服务
     *
     * 未覆写的实现（本地、DB 等存储）不支持预签名，调用方需回退服务端流式代理
     *
     * @param path              相对路径
     * @param filename          下载展示的文件名（写入 Content-Disposition）
     * @param expirationSeconds 访问有效期，单位秒
     * @return 预签名下载地址
     */
    default String presignDownloadUrl(String path, String filename, Integer expirationSeconds) {
        throw new UnsupportedOperationException("不支持的操作");
    }

}
