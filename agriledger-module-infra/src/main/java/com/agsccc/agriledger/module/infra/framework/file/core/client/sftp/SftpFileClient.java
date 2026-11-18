package com.agsccc.agriledger.module.infra.framework.file.core.client.sftp;

import com.agsccc.agriledger.module.infra.framework.file.core.client.AbstractFileClient;
import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Sftp 文件客户端
 *
 * @author Sail
 */
public class SftpFileClient extends AbstractFileClient<SftpFileClientConfig> {

    /**
     * 连接超时时间，单位：毫秒
     */
    private static final Long CONNECTION_TIMEOUT = 3000L;
    /**
     * 读写超时时间，单位：毫秒
     */
    private static final Long SO_TIMEOUT = 10000L;

    static {
        // 某些旧的 sftp 服务器仅支持 ssh-dss 协议，该协议并不安全，默认不支持该协议，按需添加
        JSch.setConfig("server_host_key", JSch.getConfig("server_host_key") + ",ssh-dss");
    }

    private Session session;
    private ChannelSftp sftp;

    public SftpFileClient(Long id, SftpFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        try {
            // 初始化 Sftp 连接
            JSch jsch = new JSch();
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(config.getPassword());
            // 与旧实现保持一致，关闭 knownHosts 严格校验
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(SO_TIMEOUT.intValue());
            session.connect(CONNECTION_TIMEOUT.intValue());
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECTION_TIMEOUT.intValue());
        } catch (Exception e) {
            disconnectQuietly();
            throw new RuntimeException(String.format("初始化 Sftp 客户端(%s:%s) 失败", config.getHost(), config.getPort()), e);
        }
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        // 执行写入
        String filePath = getFilePath(path);
        reconnectIfTimeout();
        try {
            // 需要创建父目录，不然会报错
            mkDirs(getParentPath(filePath));
            sftp.put(new ByteArrayInputStream(content), filePath);
        } catch (SftpException e) {
            throw new RuntimeException(String.format("上传文件到目标目录 (%s) 失败", filePath), e);
        }
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public String upload(InputStreamProvider content, long size, String path, String type) {
        // 流式写入，大文件无需读入内存
        String filePath = getFilePath(path);
        reconnectIfTimeout();
        try {
            // 需要创建父目录，不然会报错
            mkDirs(getParentPath(filePath));
            try (InputStream in = content.openStream()) {
                sftp.put(in, filePath);
            }
        } catch (IOException | SftpException e) {
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
            sftp.rm(filePath);
        } catch (SftpException e) {
            throw new RuntimeException(String.format("删除文件 (%s) 失败", filePath), e);
        }
    }

    @Override
    public byte[] getContent(String path) {
        String filePath = getFilePath(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reconnectIfTimeout();
        try {
            sftp.get(filePath, out);
        } catch (SftpException e) {
            throw new RuntimeException(String.format("下载文件 (%s) 失败", filePath), e);
        }
        return out.toByteArray();
    }

    private String getFilePath(String path) {
        return config.getBasePath() + "/" + path;
    }

    /**
     * 截取文件的上级目录，例如 /infra/file/a.jpg => /infra/file
     */
    private String getParentPath(String filePath) {
        int lastSeparator = filePath.lastIndexOf('/');
        return lastSeparator > 0 ? filePath.substring(0, lastSeparator) : filePath;
    }

    /**
     * 逐级创建目录，已存在时跳过，等价于 mkdir -p
     */
    private void mkDirs(String dir) throws SftpException {
        if (StringUtils.isEmpty(dir)) {
            return;
        }
        StringBuilder currentPath = new StringBuilder(dir.startsWith("/") ? "/" : "");
        for (String segment : dir.split("/")) {
            if (StringUtils.isEmpty(segment)) {
                continue;
            }
            if (currentPath.length() > 0 && currentPath.charAt(currentPath.length() - 1) != '/') {
                currentPath.append('/');
            }
            currentPath.append(segment);
            try {
                sftp.cd(currentPath.toString());
            } catch (SftpException e) {
                sftp.mkdir(currentPath.toString());
            }
        }
    }

    private synchronized void reconnectIfTimeout() {
        try {
            sftp.ls(".");
            return;
        } catch (Exception ignored) {
            // 探活失败说明连接已失效，走下方重连逻辑
        }
        disconnectQuietly();
        doInit();
    }

    /**
     * 重连前的清理，断开旧的失效连接，失败不影响重连
     */
    private void disconnectQuietly() {
        if (sftp != null && !sftp.isClosed()) {
            sftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

}
