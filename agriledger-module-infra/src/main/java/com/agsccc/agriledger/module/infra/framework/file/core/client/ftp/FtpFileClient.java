package com.agsccc.agriledger.module.infra.framework.file.core.client.ftp;

import com.agsccc.agriledger.module.infra.framework.file.core.client.AbstractFileClient;
import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import com.agsccc.agriledger.module.infra.framework.file.core.client.ftp.FtpFileClientConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Ftp 文件客户端
 *
 * @author Sail
 */
public class FtpFileClient extends AbstractFileClient<FtpFileClientConfig> {

    /**
     * 连接超时时间，单位：毫秒
     */
    private static final Long CONNECTION_TIMEOUT = 3000L;
    /**
     * 读写超时时间，单位：毫秒
     */
    private static final Long SO_TIMEOUT = 10000L;
    /**
     * 被动连接模式，对应 {@link #config} 的 mode 字符串
     */
    private static final String MODE_PASSIVE = "Passive";

    private FTPClient ftp;

    public FtpFileClient(Long id, FtpFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        ftp = new FTPClient();
        ftp.setControlEncoding(StandardCharsets.UTF_8.name());
        ftp.setConnectTimeout(CONNECTION_TIMEOUT.intValue());
        ftp.setDataTimeout(Duration.ofMillis(SO_TIMEOUT));
        try {
            // 连接并登录
            ftp.connect(config.getHost(), config.getPort());
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                ftp.disconnect();
                throw new RuntimeException(String.format("连接 Ftp 服务器(%s:%s) 失败", config.getHost(), config.getPort()));
            }
            if (!ftp.login(config.getUsername(), config.getPassword())) {
                throw new RuntimeException(String.format("登录 Ftp 服务器(%s:%s) 失败", config.getHost(), config.getPort()));
            }
            // 二进制传输，避免文本内容被转换破坏
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            // 连接模式：被动 / 主动
            if (MODE_PASSIVE.equalsIgnoreCase(config.getMode())) {
                ftp.enterLocalPassiveMode();
            } else {
                ftp.enterLocalActiveMode();
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("初始化 Ftp 客户端(%s:%s) 失败", config.getHost(), config.getPort()), e);
        }
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        // 执行写入
        String filePath = getFilePath(path);
        String fileName = new File(filePath).getName();
        String dir = StringUtils.removeEnd(filePath, fileName);
        reconnectIfTimeout();
        try {
            mkDirs(dir);
            if (!ftp.storeFile(fileName, new ByteArrayInputStream(content))) {
                throw new RuntimeException(String.format("上传文件到目标目录 (%s) 失败", filePath));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("上传文件到目标目录 (%s) 失败", filePath), e);
        }
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public String upload(InputStreamProvider content, long size, String path, String type) {
        // 流式写入，大文件无需读入内存
        String filePath = getFilePath(path);
        String fileName = new File(filePath).getName();
        String dir = StringUtils.removeEnd(filePath, fileName);
        reconnectIfTimeout();
        try (InputStream in = content.openStream()) {
            mkDirs(dir);
            if (!ftp.storeFile(fileName, in)) {
                throw new RuntimeException(String.format("上传文件到目标目录 (%s) 失败", filePath));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("上传文件到目标目录 (%s) 失败", filePath), e);
        }
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public void delete(String path) {
        String filePath = getFilePath(path);
        reconnectIfTimeout();
        try {
            ftp.deleteFile(filePath);
        } catch (IOException e) {
            throw new RuntimeException(String.format("删除文件 (%s) 失败", filePath), e);
        }
    }

    @Override
    public byte[] getContent(String path) {
        String filePath = getFilePath(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reconnectIfTimeout();
        try {
            if (!ftp.retrieveFile(filePath, out)) {
                throw new RuntimeException(String.format("下载文件 (%s) 失败", filePath));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("下载文件 (%s) 失败", filePath), e);
        }
        return out.toByteArray();
    }

    private String getFilePath(String path) {
        return config.getBasePath() + "/" + path;
    }

    /**
     * 逐级进入目录，不存在时创建，保证父目录存在
     */
    private void mkDirs(String dir) throws IOException {
        if (StringUtils.isEmpty(dir)) {
            return;
        }
        for (String segment : dir.split("/")) {
            if (StringUtils.isEmpty(segment)) {
                continue;
            }
            if (!ftp.changeWorkingDirectory(segment)) {
                ftp.makeDirectory(segment);
                ftp.changeWorkingDirectory(segment);
            }
        }
    }

    private synchronized void reconnectIfTimeout() {
        try {
            if (ftp.isConnected() && ftp.sendNoOp()) {
                return;
            }
        } catch (IOException ignored) {
            // 探活失败说明连接已失效，走下方重连逻辑
        }
        disconnectQuietly();
        doInit();
    }

    /**
     * 重连前的清理，断开旧的失效连接，失败不影响重连
     */
    private void disconnectQuietly() {
        try {
            ftp.disconnect();
        } catch (IOException ignored) {
            // 连接已失效时 disconnect 可能报错，直接忽略
        }
    }

}
