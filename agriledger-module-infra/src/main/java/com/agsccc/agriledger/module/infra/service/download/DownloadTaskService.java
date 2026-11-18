package com.agsccc.agriledger.module.infra.service.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskDownloadUrlRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskPageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import jakarta.validation.Valid;

import java.io.OutputStream;
import java.util.List;

/**
 * 下载中心任务 Service 接口
 *
 * <p>多文件、大文件异步打包 ZIP：创建即落库（排队态）并投递 Redis Stream 消息，
 * 由消费者 CAS 抢占后异步执行；产物上传文件存储，保留期内可重复下载。
 * 可见性与操作权限按创建者隔离。
 *
 * @author Sail
 */
public interface DownloadTaskService {

    /**
     * 创建下载任务：校验通过后落库（排队态）并投递执行消息。
     * 支持幂等：同一用户重复提交相同 requestId 时返回原任务
     *
     * @param userId      用户编号（任务归属者）
     * @param createReqVO 创建信息
     * @return 任务编号
     */
    Long createDownloadTask(Long userId, @Valid DownloadTaskCreateReqVO createReqVO);

    /**
     * 消费执行消息：CAS 抢占排队任务并派发到对应类型处理器执行。
     * 多实例/重复投递下仅一个消费者获得执行权（幂等）
     *
     * @param taskId 任务编号
     */
    void executeQueuedTask(Long taskId);

    /**
     * 获得当前用户的下载任务分页
     *
     * @param userId   用户编号（任务归属者）
     * @param pageReqVO 分页查询
     * @return 下载任务分页
     */
    PageResult<DownloadTaskDO> getDownloadTaskPage(Long userId, DownloadTaskPageReqVO pageReqVO);

    /**
     * 统计当前用户打包中（PROCESSING）的任务数
     *
     * @param userId 用户编号（任务归属者）
     * @return 打包中的任务数
     */
    Long getProcessingDownloadTaskCount(Long userId);

    /**
     * 获得下载任务（校验归属）
     *
     * @param userId 用户编号（任务归属者）
     * @param id     任务编号
     * @return 下载任务
     */
    DownloadTaskDO getDownloadTask(Long userId, Long id);

    /**
     * 校验任务归属且产物可下载（打包成功且未过期）
     *
     * @param userId 用户编号（任务归属者）
     * @param id     任务编号
     * @return 下载任务
     */
    DownloadTaskDO validateDownloadableTask(Long userId, Long id);

    /**
     * 获得任务产物的下载直链：支持预签名的存储返回带附件名的临时直链
     * （浏览器直连对象存储，支持 Range 断点续传），其余存储回退服务端代理下载地址
     *
     * @param userId 用户编号（任务归属者）
     * @param id     任务编号
     * @return 下载直链
     */
    DownloadTaskDownloadUrlRespVO getTaskDownloadUrl(Long userId, Long id);

    /**
     * 将任务产物流式写入输出流（内部从文件存储流式读取，不占内存）。
     * 仅作为不支持预签名存储的回退下载路径
     *
     * @param task 下载任务（须为 {@link com.agsccc.agriledger.module.infra.enums.download.DownloadTaskStatusEnum#SUCCESS}）
     * @param out  输出目标流
     */
    void streamTaskContent(DownloadTaskDO task, OutputStream out) throws Exception;

    /**
     * 取消下载任务：排队中直接取消，打包中由执行线程在下一个进度检查点协作中止
     *
     * @param userId 用户编号（任务归属者）
     * @param id     任务编号
     */
    void cancelDownloadTask(Long userId, Long id);

    /**
     * 删除下载任务（打包成功的先回收存储产物）
     *
     * @param userId 用户编号（任务归属者）
     * @param id     任务编号
     */
    void deleteDownloadTask(Long userId, Long id) throws Exception;

    /**
     * 批量删除下载任务
     *
     * @param userId 用户编号（任务归属者）
     * @param ids    任务编号列表
     */
    void deleteDownloadTaskList(Long userId, List<Long> ids) throws Exception;

}
