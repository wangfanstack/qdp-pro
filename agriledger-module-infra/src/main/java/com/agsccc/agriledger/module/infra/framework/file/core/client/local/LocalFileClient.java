package com.agsccc.agriledger.module.infra.framework.file.core.client.local;

import com.agsccc.agriledger.module.infra.framework.file.core.client.AbstractFileClient;
import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import com.agsccc.agriledger.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FilePathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.FILE_PATH_INVALID;

/**
 * 本地文件客户端
 *
 * @author Sail
 */
public class LocalFileClient extends AbstractFileClient<LocalFileClientConfig> {

    public LocalFileClient(Long id, LocalFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        // 执行写入
        String filePath = getFilePath(path);
        try {
            Files.write(Paths.get(filePath), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public String upload(InputStreamProvider content, long size, String path, String type) {
        // 流式写入，大文件无需读入内存
        String filePath = getFilePath(path);
        try (InputStream in = content.openStream()) {
            Files.copy(in, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public void delete(String path) {
        String filePath = getFilePath(path);
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] getContent(String path) {
        String filePath = getFilePath(path);
        try {
            return Files.readAllBytes(Paths.get(filePath));
        } catch (NoSuchFileException ex) {
            // 文件不存在时返回 null，由上层决定如何处理
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream getContentStream(String path) {
        String filePath = getFilePath(path);
        try {
            // 流式读取，大文件下载无需读入内存
            return Files.newInputStream(Paths.get(filePath));
        } catch (NoSuchFileException ex) {
            // 与 getContent 约定一致：不存在返回 null
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getFilePath(String path) {
        FilePathUtils.validatePath(path);
        Path basePath = Paths.get(config.getBasePath()).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(path).normalize();
        if (!filePath.startsWith(basePath)) {
            throw exception(FILE_PATH_INVALID);
        }
        return filePath.toString();
    }

}
