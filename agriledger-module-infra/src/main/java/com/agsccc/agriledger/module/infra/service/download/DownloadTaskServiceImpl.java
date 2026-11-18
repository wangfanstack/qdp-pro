package com.agsccc.agriledger.module.infra.service.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.util.json.JsonUtils;
import com.agsccc.agriledger.framework.mq.redis.core.RedisMQTemplate;
import com.agsccc.agriledger.framework.tenant.core.context.TenantContextHolder;
import com.agsccc.agriledger.framework.tenant.core.util.TenantUtils;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskDownloadUrlRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskPageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.agsccc.agriledger.module.infra.dal.mysql.download.DownloadTaskMapper;
import com.agsccc.agriledger.module.infra.enums.download.DownloadTaskTypeEnum;
import com.agsccc.agriledger.module.infra.framework.download.config.DownloadTaskProperties;
import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClient;
import com.agsccc.agriledger.module.infra.mq.DownloadTaskMessage;
import com.agsccc.agriledger.module.infra.service.download.DownloadTaskHandler;
import com.agsccc.agriledger.module.infra.service.file.FileConfigService;
import com.agsccc.agriledger.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.*;
import static com.agsccc.agriledger.module.infra.enums.download.DownloadTaskStatusEnum.*;

/**
 * 下载中心任务 Service 实现类
 *
 * <p>执行模型：createDownloadTask 落库任务（QUEUED）并投递 Redis Stream 消息；
 * 消费者 executeQueuedTask 以 CAS（QUEUED→PROCESSING）抢占执行权后，提交打包
 * 执行线程池派发到对应类型的 {@link DownloadTaskHandler}。任务表是唯一状态源，
 * 进度更新兼作打包进程心跳；终态转移一律条件化更新（CAS），保证与用户取消
 * 并发时状态不倒退。消息丢失/消费者崩溃由 DownloadTaskCleanJob 扫描补偿重投。
 *
 * @author Sail
 */
@Service
@Slf4j
public class DownloadTaskServiceImpl implements DownloadTaskService {

    /**
     * 排队中任务的占位过期时长（小时）：防清理任务误回收未完成任务，成功后刷新为保留期
     */
    private static final int PLACEHOLDER_EXPIRE_HOURS = 1;
    private static final DateTimeFormatter NAME_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private DownloadTaskMapper downloadTaskMapper;
    @Resource
    private FileService fileService;
    @Resource
    private FileConfigService fileConfigService;
    @Resource
    private DownloadTaskProperties downloadTaskProperties;
    @Resource
    private RedisMQTemplate redisMQTemplate;

    @Resource
    @Qualifier("fileZipDownloadExecutor")
    private ThreadPoolTaskExecutor fileZipDownloadExecutor;

    /**
     * 任务类型 → 处理器注册表（Spring 注入全部实现，业务模块新增 Bean 即接入）
     */
    private final Map<Integer, DownloadTaskHandler> handlerRegistry = new HashMap<>();

    /**
     * 构造注入全部任务处理器并按类型注册
     */
    public DownloadTaskServiceImpl(List<DownloadTaskHandler> handlers) {
        for (DownloadTaskHandler handler : handlers) {
            DownloadTaskHandler exist = handlerRegistry.put(handler.getType(), handler);
            if (exist != null) {
                throw new IllegalStateException(String.format("下载任务类型(%s) 存在重复处理器(%s/%s)",
                        handler.getType(), exist.getClass().getName(), handler.getClass().getName()));
            }
        }
    }

    @Override
    public Long createDownloadTask(Long userId, DownloadTaskCreateReqVO createReqVO) {
        String creator = String.valueOf(userId);
        // 1. 幂等检查：同一用户重复提交相同 requestId（如连续点击）时返回原任务，避免产出多个 ZIP
        if (StringUtils.isNotEmpty(createReqVO.getRequestId())) {
            DownloadTaskDO existTask = downloadTaskMapper.selectByCreatorAndRequestId(creator, createReqVO.getRequestId());
            if (existTask != null) {
                log.info("[createDownloadTask][幂等命中,返回原任务 taskId({}) requestId({})]", existTask.getId(), createReqVO.getRequestId());
                return existTask.getId();
            }
        }
        // 2. 解析任务类型对应的处理器（文件 ZIP 为内置，Excel 等由业务模块注册）
        Integer type = createReqVO.getType() != null ? createReqVO.getType() : DownloadTaskTypeEnum.FILE_ZIP.getType();
        if (!handlerRegistry.containsKey(type)) {
            throw exception(DOWNLOAD_TASK_TYPE_NOT_SUPPORT);
        }
        // 3. 校验文件存在与任务规模（部分 id 查不到时容忍，按实际查到的打包；当前仅文件 ZIP 携带文件清单）
        List<FileDO> files = fileService.getFileList(createReqVO.getFileIds());
        if (CollectionUtils.isEmpty(files)) {
            throw exception(FILE_NOT_EXISTS);
        }
        if (files.size() > downloadTaskProperties.getMaxFileCount()) {
            throw exception(DOWNLOAD_TASK_SIZE_EXCEED);
        }
        long totalSize = files.stream().mapToLong(file -> file.getSize() != null ? file.getSize() : 0L).sum();
        if (totalSize > downloadTaskProperties.getMaxTaskSize()) {
            throw exception(DOWNLOAD_TASK_SIZE_EXCEED);
        }
        // 4. 校验用户并发上限（排队中与打包中均占用额度）
        if (downloadTaskMapper.selectCountByCreatorAndStatusIn(creator, List.of(QUEUED.getStatus(), PROCESSING.getStatus()))
                >= downloadTaskProperties.getMaxUserProcessingTasks()) {
            throw exception(DOWNLOAD_TASK_USER_LIMIT);
        }
        // 5. 落库任务记录（QUEUED）：文件清单与租户快照持久化，
        // 执行时重载文件清单（排队期间文件可能被删除）并按快照恢复租户上下文
        DownloadTaskDO task = DownloadTaskDO.builder()
                .name(buildTaskName(createReqVO.getName()))
                .type(type)
                .status(QUEUED.getStatus())
                .requestId(createReqVO.getRequestId())
                .tenantId(TenantContextHolder.getTenantId())
                .fileIds(JsonUtils.toJsonString(createReqVO.getFileIds()))
                .totalCount(files.size())
                .totalBytes(totalSize)
                .expiredTime(LocalDateTime.now().plusHours(PLACEHOLDER_EXPIRE_HOURS))
                .build();
        downloadTaskMapper.insert(task);
        // 6. 投递执行消息（只传编号，MySQL 为唯一事实来源）；发送失败不阻断创建，由清理任务补偿重投
        sendTaskMessage(task.getId());
        log.info("[createDownloadTask][任务已创建并投递 taskId({}) userId({}) files({}) bytes({})]",
                task.getId(), userId, files.size(), totalSize);
        return task.getId();
    }

    @Override
    public void executeQueuedTask(Long taskId) {
        // 1. 幂等检查：非排队态直接跳过（重复投递/已取消/已在执行）
        DownloadTaskDO task = downloadTaskMapper.selectById(taskId);
        if (task == null || !QUEUED.getStatus().equals(task.getStatus())) {
            log.info("[executeQueuedTask][任务非排队态,跳过 taskId({})]", taskId);
            return;
        }
        // 2. CAS 抢占执行权（多实例/重复投递下仅一个消费者成功），抢占后回读最新任务
        if (!downloadTaskMapper.updateStatusIf(taskId, QUEUED.getStatus(), PROCESSING.getStatus())) {
            log.info("[executeQueuedTask][任务抢占失败,已被其他消费者执行 taskId({})]", taskId);
            return;
        }
        DownloadTaskHandler handler = handlerRegistry.get(task.getType());
        if (handler == null) {
            downloadTaskMapper.updateTaskFailedIfProcessing(taskId, "任务类型无处理器");
            log.error("[executeQueuedTask][任务类型无处理器 taskId({}) type({})]", taskId, task.getType());
            return;
        }
        // 3. 提交打包执行线程池异步执行，消费线程立即返回；
        //    执行时以任务记录的租户快照恢复上下文，不依赖 MQ 消费线程的瞬时上下文
        //    （MQ 重试、服务重启、跨实例补偿后执行依然正确）
        try {
            fileZipDownloadExecutor.execute(() -> executeTaskWithTenantContext(task, handler));
        } catch (RejectedExecutionException e) {
            // 执行池满载：退回排队态等待补偿重投（不立即重发消息，防集群间热循环）
            log.warn("[executeQueuedTask][执行池已满,任务退回排队 taskId({})]", taskId);
            downloadTaskMapper.updateStatusIf(taskId, PROCESSING.getStatus(), QUEUED.getStatus());
        } catch (RuntimeException e) {
            // 提交链路意外异常同样退回排队态，杜绝任务永久卡在 PROCESSING
            log.error("[executeQueuedTask][任务提交异常,退回排队 taskId({})]", taskId, e);
            downloadTaskMapper.updateStatusIf(taskId, PROCESSING.getStatus(), QUEUED.getStatus());
        }
    }

    /**
     * 以任务记录的租户快照恢复租户上下文后执行处理器。
     * 快照为空（如无租户上下文的系统调用）时直接执行，避免向上下文写入空租户
     */
    private void executeTaskWithTenantContext(DownloadTaskDO task, DownloadTaskHandler handler) {
        if (task.getTenantId() == null) {
            handler.executeTask(task);
            return;
        }
        TenantUtils.execute(task.getTenantId(), () -> handler.executeTask(task));
    }

    @Override
    public PageResult<DownloadTaskDO> getDownloadTaskPage(Long userId, DownloadTaskPageReqVO pageReqVO) {
        return downloadTaskMapper.selectPageByCreator(String.valueOf(userId), pageReqVO);
    }

    @Override
    public Long getProcessingDownloadTaskCount(Long userId) {
        return downloadTaskMapper.selectCountByCreatorAndStatus(String.valueOf(userId), Set.of(QUEUED.getStatus(), PROCESSING.getStatus()));
    }

    @Override
    public DownloadTaskDO getDownloadTask(Long userId, Long id) {
        DownloadTaskDO task = downloadTaskMapper.selectById(id);
        if (task == null) {
            throw exception(DOWNLOAD_TASK_NOT_EXISTS);
        }
        validateTaskOwner(task, userId);
        return task;
    }

    @Override
    public DownloadTaskDO validateDownloadableTask(Long userId, Long id) {
        DownloadTaskDO task = getDownloadTask(userId, id);
        if (EXPIRED.getStatus().equals(task.getStatus()) || CANCELLED.getStatus().equals(task.getStatus())) {
            // 产物已被回收或未生成，语义上视同任务不存在
            throw exception(DOWNLOAD_TASK_NOT_EXISTS);
        }
        if (!SUCCESS.getStatus().equals(task.getStatus())) {
            throw exception(DOWNLOAD_TASK_NOT_READY);
        }
        return task;
    }

    @Override
    public DownloadTaskDownloadUrlRespVO getTaskDownloadUrl(Long userId, Long id) {
        DownloadTaskDO task = validateDownloadableTask(userId, id);
        DownloadTaskDownloadUrlRespVO respVO = new DownloadTaskDownloadUrlRespVO();
        try {
            FileClient client = fileConfigService.getFileClient(task.getStorageConfigId());
            Assert.notNull(client, String.format("客户端(%s) 不能为空", task.getStorageConfigId()));
            // 预签名直链：浏览器直连对象存储下载（支持 Range 断点续传），文件流量不经过应用服务
            respVO.setDownloadUrl(client.presignDownloadUrl(task.getStoragePath(), task.getName(),
                    downloadTaskProperties.getUrlExpireSeconds()));
            respVO.setExpireSeconds(downloadTaskProperties.getUrlExpireSeconds());
        } catch (UnsupportedOperationException e) {
            // 存储不支持预签名（本地/DB/FTP/SFTP）：回退服务端流式代理下载，前端拼接网关前缀直接访问
            respVO.setDownloadUrl("infra/download-task/download?id=" + task.getId());
        }
        return respVO;
    }

    @Override
    public void streamTaskContent(DownloadTaskDO task, OutputStream out) throws Exception {
        FileClient client = fileConfigService.getFileClient(task.getStorageConfigId());
        Assert.notNull(client, String.format("客户端(%s) 不能为空", task.getStorageConfigId()));
        // 从文件存储流式读取产物，全程不整块读入内存
        try (InputStream in = client.getContentStream(task.getStoragePath())) {
            if (in == null) {
                // 产物已被回收（清理任务与状态登记之间的极端窗口）
                throw exception(DOWNLOAD_TASK_NOT_EXISTS);
            }
            StreamUtils.copy(in, out);
        }
    }

    @Override
    public void cancelDownloadTask(Long userId, Long id) {
        // 归属校验后条件化转移：排队中直接取消，打包中由执行线程在下一个进度检查点协作中止
        getDownloadTask(userId, id);
        boolean cancelled = downloadTaskMapper.updateStatusIf(id, QUEUED.getStatus(), CANCELLED.getStatus())
                || downloadTaskMapper.updateStatusIf(id, PROCESSING.getStatus(), CANCELLED.getStatus());
        if (!cancelled) {
            throw exception(DOWNLOAD_TASK_CANNOT_CANCEL);
        }
        log.info("[cancelDownloadTask][任务取消已受理 taskId({}) userId({})]", id, userId);
    }

    @Override
    public void deleteDownloadTask(Long userId, Long id) throws Exception {
        DownloadTaskDO task = getDownloadTask(userId, id);
        // 打包成功的先回收存储产物，再删除记录；产物删除失败时保留记录供重试
        if (SUCCESS.getStatus().equals(task.getStatus()) && task.getStorageConfigId() != null) {
            FileClient client = fileConfigService.getFileClient(task.getStorageConfigId());
            if (client != null) {
                try {
                    client.delete(task.getStoragePath());
                } catch (Exception e) {
                    log.error("[deleteDownloadTask][产物删除失败 taskId({}) path({})]", id, task.getStoragePath(), e);
                    throw exception(DOWNLOAD_TASK_FILE_DELETE_FAILED);
                }
            }
        }
        downloadTaskMapper.deleteById(id);
    }

    @Override
    public void deleteDownloadTaskList(Long userId, List<Long> ids) throws Exception {
        for (Long id : ids) {
            deleteDownloadTask(userId, id);
        }
    }

    /**
     * 投递任务执行消息（补偿重投共用）；发送失败仅告警，任务停留排队态等待下轮扫描
     */
    private void sendTaskMessage(Long taskId) {
        try {
            redisMQTemplate.send(DownloadTaskMessage.builder().taskId(taskId).build());
        } catch (Exception e) {
            log.error("[sendTaskMessage][消息发送失败,等待补偿重投 taskId({})]", taskId, e);
        }
    }

    private void validateTaskOwner(DownloadTaskDO task, Long userId) {
        if (!String.valueOf(userId).equals(task.getCreator())) {
            throw exception(DOWNLOAD_TASK_NOT_OWNER);
        }
    }

    /**
     * 任务名缺省按时间生成，统一补齐 .zip 后缀（作为下载文件名展示）
     */
    private String buildTaskName(String name) {
        if (StringUtils.isBlank(name)) {
            return "批量下载_" + LocalDateTime.now().format(NAME_TIMESTAMP_FORMATTER) + ".zip";
        }
        return Strings.CS.endsWith(name, ".zip") ? name : name + ".zip";
    }

}
