package com.agsccc.agriledger.module.infra.framework.file.core.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件内容流提供者，每次调用返回一个新的输入流
 *
 * 用于大文件流式上传：S3 签名或失败重试时需要重新读取内容，
 * 单一输入流无法重复消费，必须支持重新打开（例如 multipart 底层的磁盘临时文件）
 *
 * @author Sail
 */
@FunctionalInterface
public interface InputStreamProvider {

    /**
     * 打开一个新的内容输入流，由调用方负责关闭
     *
     * @return 内容输入流
     * @throws IOException 打开流失败时抛出
     */
    InputStream openStream() throws IOException;

}
