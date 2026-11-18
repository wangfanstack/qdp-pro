package com.agsccc.agriledger.module.infra.service.file;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.util.crypto.CryptoUtils;
import com.agsccc.agriledger.framework.common.util.http.HttpUtils;
import com.agsccc.agriledger.framework.tenant.core.context.TenantContextHolder;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileDownloadUrlRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import com.agsccc.agriledger.module.infra.convert.file.FileConvert;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.agsccc.agriledger.module.infra.dal.mysql.file.FileMapper;
import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClient;
import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import com.agsccc.agriledger.module.infra.framework.file.core.enums.FileCategoryEnum;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FilePathUtils;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FileTypeUtils;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FileZipUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;

/**
 * 文件 Service 实现类
 *
 * @author Sail
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    /**
     * 单文件下载直链有效期（秒），与下载中心预签名直链保持一致的短有效期约定
     */
    private static final int DOWNLOAD_URL_EXPIRE_SECONDS = 600;

    /**
     * 外部文件登记时的存储配置占位编号：文件本体在外部存储（如 TRTC 录制 COS 桶），
     * 不归属任何文件存储配置，与 notary_material 出证报告占位 fileId=0 的约定一致
     */
    static final Long EXTERNAL_FILE_CONFIG_ID = 0L;

    @Resource
    private FileConfigService fileConfigService;

    @Resource
    private FileMapper fileMapper;

    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO) {
        return fileMapper.selectPage(pageReqVO);
    }

    @Override
    @SneakyThrows
    public Map<String, Object> createFile(byte[] content, String name, String category, String type) {
        // 1.1 处理 name 的合法性，禁止携带目录路径
        name = FilePathUtils.validateFileName(name);
        // 1.2 处理 type 为空的情况
        if (StringUtils.isEmpty(type)) {
            type = FileTypeUtils.getMineType(content, name);
        }
        // 1.3 处理 name 为空的情况：基于内容 sha256 命名
        if (StringUtils.isEmpty(name)) {
            name = CryptoUtils.sha256Hex(content);
        }
        // 2. 上传到文件存储器并登记
        return doCreateFile(name, category, type, content.length,
                (client, path, uploadType) -> client.upload(content, path, uploadType));
    }

    @Override
    @SneakyThrows
    public Map<String, Object> createFile(InputStreamProvider content, long size, String name, String category, String type) {
        // 1. 名字为空时需要基于内容 sha256 命名，降级读内存处理（该场景罕见，行为与 byte[] 版本一致）
        if (StringUtils.isEmpty(FilePathUtils.validateFileName(name))) {
            try (InputStream in = content.openStream()) {
                return createFile(StreamUtils.copyToByteArray(in), name, category, type);
            }
        }
        // 2. 处理 type 为空的情况：独立打开一次流探测，只读少量头部字节即关闭，不影响上传内容
        if (StringUtils.isEmpty(type)) {
            try (InputStream headStream = content.openStream()) {
                type = FileTypeUtils.getMineType(headStream, name);
            }
        }
        // 3. 上传到文件存储器并登记
        return doCreateFile(name, category, type, size,
                (client, path, uploadType) -> client.upload(content, size, path, uploadType));
    }

    /**
     * 上传到文件存储器的动作，屏蔽 byte[] 与流式两种上传形态的差异
     */
    @FunctionalInterface
    private interface FileUploader {

        String upload(FileClient client, String path, String type) throws Exception;
    }

    private Map<String, Object> doCreateFile(String name, String category, String type, long size, FileUploader uploader) throws Exception {
        // 1.1 处理 name 的合法性，禁止携带目录路径
        name = FilePathUtils.validateFileName(name);
        // 1.2 处理 name 无后缀的情况：按 type 补充后缀
        if (StringUtils.isEmpty(Files.getFileExtension(name))) {
            String extension = FileTypeUtils.getExtension(type);
            if (StringUtils.isNotEmpty(extension)) {
                name = name + extension;
            }
        }
        // 2.1 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, type, category);
        // 2.2 上传到文件存储器
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = uploader.upload(client, path, type);
        // 3. 保存到数据库
        FileDO fileDO = new FileDO().setConfigId(client.getId())
                .setName(name).setPath(path)
                .setUrl(url)
                .setType(type).setSize(size);
        fileMapper.insert(fileDO);

        Map<String, Object> res = Maps.newHashMap();
        res.put("url", url);
        res.put("fileId", fileDO.getId());
        return res;
    }

    @VisibleForTesting
    String generateUploadPath(String name, String type, String category) {
        // 1.1 处理 name 的合法性，禁止携带目录路径
        name = FilePathUtils.validateFileName(name);
        FilePathUtils.validatePath(name);
        // 1.2 解析分类：显式指定优先，未指定或非法时按 MIME/扩展名自动推断
        FileCategoryEnum categoryEnum = FileCategoryEnum.of(category);
        if (categoryEnum == null) {
            categoryEnum = FileCategoryEnum.infer(name, type);
        }
        // 1.3 按目录规范生成路径：{category}/{tenantId}/{yyyy}/{MM}/{uuid}.{ext}，租户缺失时落缺省段
        return FilePathUtils.buildStoragePath(categoryEnum, TenantContextHolder.getTenantId(), Files.getFileExtension(name));
    }

    @Override
    @SneakyThrows
    public FilePresignedUrlRespVO presignPutUrl(String name, String category) {
        // 1. 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, null, category);

        // 2. 获取文件预签名地址
        FileClient fileClient = fileConfigService.getMasterFileClient();
        String uploadUrl = fileClient.presignPutUrl(path);
        String visitUrl = fileClient.presignGetUrl(path, null);
        return new FilePresignedUrlRespVO().setConfigId(fileClient.getId())
                .setPath(path).setUploadUrl(uploadUrl).setUrl(visitUrl);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        FileClient fileClient = fileConfigService.getMasterFileClient();
        return fileClient.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public Long createFile(FileCreateReqVO createReqVO) {
        // 1.1 校验参数的合法性
        FilePathUtils.validatePath(createReqVO.getPath());
        createReqVO.setName(FilePathUtils.validateFileName(createReqVO.getName()));
        // 1.2 处理 URL 的合法性，移除 URL 中的查询参数（例如签名参数），保证 URL 的唯一性
        createReqVO.setUrl(HttpUtils.removeUrlQuery(createReqVO.getUrl())); // 目的：移除私有桶情况下，URL 的签名参数

        // 2. 保存到数据库
        FileDO file = FileConvert.INSTANCE.convert(createReqVO);
        fileMapper.insert(file);
        return file.getId();
    }

    @Override
    public Long createFileRecord(String name, String url) {
        FileClient client = fileConfigService.getMasterFileClient();
        name = FilePathUtils.validateFileName(name);
        // 与 createFile(FileCreateReqVO) 不同，外部文件 URL 原样保留：
        // VOD 直链等地址的查询参数是访问凭证，移除后将无法访问
        FileDO file = new FileDO()
                .setConfigId(client.getId())
                .setName(name)
                .setPath(extractExternalPath(url))
                .setUrl(url)
                .setType(FileTypeUtils.getMineType(name));
        fileMapper.insert(file);
        return file.getId();
    }

    /**
     * 从外部文件 URL 提取存储路径（去掉协议与域名，与自有存储的 path 风格对齐）
     */
    private String extractExternalPath(String url) {
        try {
            String path = URI.create(url).getPath();
            return StringUtils.startsWith(path, "/") ? path.substring(1) : path;
        } catch (Exception e) {
            // URL 不合法时退化为文件名，path 仅作展示用途，不影响访问
            log.warn("[extractExternalPath][URL 路径解析失败，退化为文件名 url={}]", url, e);
            return null;
        }
    }

    @Override
    public FileDO getFile(Long id) {
        return validateFileExists(id);
    }

    @Override
    public void deleteFile(Long id) throws Exception {
        // 1.1 校验存在
        FileDO file = validateFileExists(id);
        // 1.2 校验路径合法性，避免误删文件存储器中的其他文件
        FilePathUtils.validatePath(file.getPath());

        // 2.1 从文件存储器中删除
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, String.format("客户端(%s) 不能为空", file.getConfigId()));
        client.delete(file.getPath());

        // 2.2 删除记录
        fileMapper.deleteById(id);
    }

    @Override
    @SneakyThrows
    public void deleteFileList(List<Long> ids) {
        // 删除文件
        List<FileDO> files = fileMapper.selectByIds(ids);
        for (FileDO file : files) {
            FilePathUtils.validatePath(file.getPath());
            // 获取客户端
            FileClient client = fileConfigService.getFileClient(file.getConfigId());
            Assert.notNull(client, String.format("客户端(%s) 不能为空", file.getPath()));
            // 删除文件
            client.delete(file.getPath());
        }

        // 删除记录
        fileMapper.deleteByIds(ids);
    }

    private FileDO validateFileExists(Long id) {
        FileDO fileDO = fileMapper.selectById(id);
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        return fileDO;
    }

    @Override
    public byte[] getFileContent(Long configId, String path) throws Exception {
        // 1. 校验路径合法性
        FilePathUtils.validatePath(path);

        // 2.1 获取客户端
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, String.format("客户端(%s) 不能为空", configId));
        // 2.2 获取文件内容
        return client.getContent(path);
    }

    @Override
    public InputStream getFileContentStream(Long configId, String path) throws Exception {
        // 1. 校验路径合法性
        FilePathUtils.validatePath(path);

        // 2. 流式打开内容（返回的流由调用方负责关闭）
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, String.format("客户端(%s) 不能为空", configId));
        return client.getContentStream(path);
    }

    @Override
    public FileDO getFileByConfigIdAndPath(Long configId, String path) {
        return fileMapper.selectLatestByConfigIdAndPath(configId, path);
    }

    @Override
    public List<FileDO> getFileList(List<Long> ids) {
        return fileMapper.selectByIds(ids);
    }

    @Override
    public FileDownloadUrlRespVO getFileDownloadUrl(Long id) {
        FileDO file = validateFileExists(id);
        String filename = StringUtils.isNotEmpty(file.getName()) ? file.getName() : new File(file.getPath()).getName();
        FileDownloadUrlRespVO respVO = new FileDownloadUrlRespVO();
        respVO.setFileId(file.getId());
        respVO.setFileName(filename);
        try {
            FileClient client = fileConfigService.getFileClient(file.getConfigId());
            Assert.notNull(client, String.format("客户端(%s) 不能为空", file.getConfigId()));
            // 预签名直链：浏览器直连对象存储下载（支持 Range 断点续传），文件流量不经过应用服务
            respVO.setDownloadUrl(client.presignDownloadUrl(file.getPath(), filename, DOWNLOAD_URL_EXPIRE_SECONDS));
            respVO.setExpireSeconds(DOWNLOAD_URL_EXPIRE_SECONDS);
        } catch (UnsupportedOperationException e) {
            // 存储不支持预签名（本地/DB/FTP/SFTP）：回退文件既有访问地址（服务端代理下载）
            respVO.setDownloadUrl(file.getUrl());
        }
        return respVO;
    }

    @Override
    public void streamFileZip(List<FileDO> files, OutputStream out) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            // 同步下载沿用历史行为：内容不存在的文件跳过继续（头部已写出，无法回退报错）
            FileZipUtils.writeZipEntries(files, zos, this::openFileContentStream, null, false, null);
        }
    }

    /**
     * 按文件记录从其所属存储打开内容流，供 zip 流式打包内核回调
     */
    private InputStream openFileContentStream(FileDO file) throws Exception {
        return getFileContentStream(file.getConfigId(), file.getPath());
    }

}
