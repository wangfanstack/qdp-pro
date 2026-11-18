package com.agsccc.agriledger.module.infra.service.file;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileDownloadUrlRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import jakarta.validation.constraints.NotEmpty;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件 Service 接口
 *
 * @author Sail
 */
public interface FileService {

    /**
     * 获得文件分页
     *
     * @param pageReqVO 分页查询
     * @return 文件分页
     */
    PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO);

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content  文件内容
     * @param name     文件名称，允许空
     * @param category 文件分类（{@link com.agsccc.agriledger.module.infra.framework.file.core.enums.FileCategoryEnum}），
     *                 允许空：空时按文件类型自动推断
     * @param type     文件的 MIME 类型，允许空
     * @return 文件路径
     */
    Map<String, Object> createFile(@NotEmpty(message = "文件内容不能为空") byte[] content, String name, String category, String type);

    /**
     * 流式保存文件，配合大文件上传使用，避免整个文件读入内存
     *
     * @param content  内容流提供者，支持重复打开（探测类型与上传各自独立开流）
     * @param size     文件大小
     * @param name     文件名称，允许空；为空时降级读取到内存后按内容 sha256 命名
     * @param category 文件分类，允许空：空时按文件类型自动推断
     * @param type     文件的 MIME 类型，允许空；为空时基于流头部探测
     * @return 文件编号与访问路径
     */
    Map<String, Object> createFile(InputStreamProvider content, long size, String name, String category, String type);

    /**
     * 生成文件预签名地址信息，用于上传
     *
     * @param name     文件名
     * @param category 文件分类，允许空：空时按文件名扩展名推断
     * @return 预签名地址信息
     */
    FilePresignedUrlRespVO presignPutUrl(@NotEmpty(message = "文件名不能为空") String name, String category);

    /**
     * 生成文件预签名地址信息，用于读取
     *
     * @param url               完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    String presignGetUrl(String url, Integer expirationSeconds);

    /**
     * 创建文件
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createFile(FileCreateReqVO createReqVO);

    /**
     * 登记外部文件 URL（不搬运文件内容，文件本体在外部存储，如 TRTC 云端录制的 COS 桶）
     *
     * @param name 文件名称
     * @param url  完整的文件访问地址
     * @return 文件编号
     */
    Long createFileRecord(@NotEmpty(message = "文件名不能为空") String name,
                          @NotEmpty(message = "URL 不能为空") String url);

    FileDO getFile(Long id);

    /**
     * 删除文件
     *
     * @param id 编号
     */
    void deleteFile(Long id) throws Exception;

    /**
     * 批量删除文件
     *
     * @param ids 编号列表
     */
    void deleteFileList(List<Long> ids) throws Exception;

    /**
     * 获得文件内容
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 文件内容
     */
    byte[] getFileContent(Long configId, String path) throws Exception;

    /**
     * 获得文件内容的输入流（流式读取，配合大文件下载与打包，避免整块读入内存）。
     * 返回的流由调用方负责关闭
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 内容输入流，存储中不存在时返回 null
     */
    InputStream getFileContentStream(Long configId, String path) throws Exception;

    /**
     * 获得文件
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 文件
     */
    FileDO getFileByConfigIdAndPath(Long configId, String path);

    /**
     * 根据 id 列表查询文件记录
     *
     * @param ids 编号列表
     * @return 文件列表
     */
    List<FileDO> getFileList(List<Long> ids);

    /**
     * 获得单文件下载直链：支持预签名的存储返回带附件名的临时直链
     * （浏览器直连对象存储，支持 Range 断点续传），其余存储回退文件既有访问地址
     *
     * @param id 文件编号
     * @return 下载直链
     */
    FileDownloadUrlRespVO getFileDownloadUrl(Long id);

    /**
     * 将文件流式打包成 zip 写入输出流（内部逐文件流式读取、固定缓冲区拷贝，内存占用与文件规模无关）。
     * <p>
     * 存储中读取失败或丢失的文件会被跳过并记录告警，不影响其余文件打包。
     * 异步任务场景请使用下载中心 {@link com.agsccc.agriledger.module.infra.service.download.DownloadTaskService}。
     *
     * @param files 文件记录
     * @param out   zip 输出目标流
     */
    void streamFileZip(List<FileDO> files, OutputStream out) throws Exception;

}
