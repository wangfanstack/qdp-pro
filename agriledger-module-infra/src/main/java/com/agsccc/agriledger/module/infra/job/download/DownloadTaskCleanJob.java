package com.agsccc.agriledger.module.infra.job.download;

import com.agsccc.agriledger.framework.mq.redis.core.RedisMQTemplate;
import com.agsccc.agriledger.framework.quartz.core.handler.JobHandler;
import com.agsccc.agriledger.framework.tenant.core.aop.TenantIgnore;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import com.agsccc.agriledger.module.infra.dal.mysql.download.DownloadTaskMapper;
import com.agsccc.agriledger.module.infra.framework.download.config.DownloadTaskProperties;
import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClient;
import com.agsccc.agriledger.module.infra.mq.DownloadTaskMessage;
import com.agsccc.agriledger.module.infra.service.file.FileConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.agsccc.agriledger.module.infra.enums.download.DownloadTaskStatusEnum.*;

/**
 * 下载中心清理任务
 *
 * <p>五项职责（建议 10 分钟调度一次）：
 * 1. 排队补偿：排队中但停滞超时的任务重投执行消息（消息丢失/执行池退回的兜底）；
 * 2. 中断恢复：打包中但心跳（update_time）停滞超时的任务，未超重试上限的回到
 *    排队态重投，超上限的置为失败（覆盖服务重启、进程崩溃场景）；
 * 3. 回收过期产物：打包成功且超过保留期的任务，删除存储产物并置为已过期；
 * 4. 清理历史记录：终态任务记录超期后软删；
 * 5. 兜底清理本地临时 zip（正常路径打包完成即删，此处防御异常遗留）。
 *
 * <p>重复投递由消费端 CAS 抢占幂等吸收，重投无副作用。
 * infra_job 登记记录由 Flyway 初始化，Quartz 触发器由
 *
 * @author Sail
 */
@Slf4j
@Component
public class DownloadTaskCleanJob implements JobHandler {

    /**
     * 打包临时 zip 文件名前缀（与 FileZipDownloadTaskHandler 保持一致）
     */
    static final String TEMP_FILE_PREFIX = "hft-file-zip-";
    /**
     * 临时 zip 保留时长（分钟）：正常路径打包完成即删，此处仅兜底异常遗留
     */
    private static final long TEMP_RETAIN_MINUTES = 30L;
    /**
     * 终态（失败/已取消/已过期）任务记录的保留时长（天）
     */
    private static final long RECORD_RETAIN_DAYS = 30L;

    @Resource
    private DownloadTaskMapper downloadTaskMapper;
    @Resource
    private FileConfigService fileConfigService;
    @Resource
    private DownloadTaskProperties downloadTaskProperties;
    @Resource
    private RedisMQTemplate redisMQTemplate;

    @Override
    @TenantIgnore
    public String execute(String param) {
        int requeuedCount = resendStaleQueuedTasks();
        int interruptedCount = recoverInterruptedTasks();
        int expiredCount = recycleExpiredTasks();
        int recordCount = cleanObsoleteRecords();
        int tempFileCount = cleanTempFiles();
        String result = String.format("排队补偿 %s 个，中断恢复 %s 个，过期回收 %s 个，记录清理 %s 条，临时文件清理 %s 个", requeuedCount, interruptedCount, expiredCount, recordCount, tempFileCount);
        log.info("[execute][下载中心清理完成：{}]", result);
        return result;
    }

    /**
     * 排队补偿：排队中但停滞超时的任务重投消息（发送失败或执行池满退回的兜底）。
     * 任务状态不变，重复投递由消费端 CAS 吸收
     */
    private int resendStaleQueuedTasks() {
        List<DownloadTaskDO> tasks = downloadTaskMapper.selectListByStatusAndUpdateTimeBefore(
                QUEUED.getStatus(), LocalDateTime.now().minusMinutes(downloadTaskProperties.getQueuedStaleMinutes()));
        for (DownloadTaskDO task : tasks) {
            sendTaskMessage(task.getId());
            log.warn("[resendStaleQueuedTasks][排队停滞,补偿重投 taskId({})]", task.getId());
        }
        return tasks.size();
    }

    /**
     * 中断恢复：打包中心跳停滞的任务，未超重试上限的回排队态并重投（换实例/换线程重试），
     * 超上限的置为失败。条件化更新兜底：扫描期间任务恰好完成/取消时不改写终态
     */
    private int recoverInterruptedTasks() {
        List<DownloadTaskDO> tasks = downloadTaskMapper.selectListByStatusAndUpdateTimeBefore(
                PROCESSING.getStatus(), LocalDateTime.now().minusMinutes(downloadTaskProperties.getProcessingStaleMinutes()));
        int recovered = 0;
        for (DownloadTaskDO task : tasks) {
            int currentRetry = task.getRetryCount() != null ? task.getRetryCount() : 0;
            if (currentRetry < downloadTaskProperties.getMaxRetryCount() && downloadTaskMapper.updateTaskRequeueIfProcessing(task.getId(), currentRetry + 1)) {
                sendTaskMessage(task.getId());
                recovered++;
                log.warn("[recoverInterruptedTasks][心跳停滞,重排队重试 taskId({}) retry({}/{})]",
                        task.getId(), currentRetry + 1, downloadTaskProperties.getMaxRetryCount());
                continue;
            }
            if (downloadTaskMapper.updateTaskFailedIfProcessing(task.getId(), "打包多次中断，请重新发起")) {
                log.warn("[recoverInterruptedTasks][心跳停滞且超重试上限,置为失败 taskId({}) retry({})]",
                        task.getId(), currentRetry);
            }
        }
        return recovered;
    }

    /**
     * 回收过期产物：删除存储文件成功后才置为已过期，删除失败保留下轮重试
     */
    private int recycleExpiredTasks() {
        List<DownloadTaskDO> tasks = downloadTaskMapper.selectListByStatusAndExpiredTimeBefore(
                SUCCESS.getStatus(), LocalDateTime.now());
        int recycled = 0;
        for (DownloadTaskDO task : tasks) {
            if (!deleteStorageFileQuietly(task)) {
                continue;
            }
            if (downloadTaskMapper.updateStatusIf(task.getId(), SUCCESS.getStatus(), EXPIRED.getStatus())) {
                recycled++;
            }
        }
        return recycled;
    }

    /**
     * 清理历史记录：失败/已取消/已过期的任务记录超期后软删
     */
    private int cleanObsoleteRecords() {
        List<DownloadTaskDO> tasks = downloadTaskMapper.selectListByStatusInAndUpdateTimeBefore(
                Arrays.asList(FAILED.getStatus(), EXPIRED.getStatus(), CANCELLED.getStatus()),
                LocalDateTime.now().minusDays(RECORD_RETAIN_DAYS));
        if (!tasks.isEmpty()) {
            downloadTaskMapper.deleteByIds(tasks.stream().map(DownloadTaskDO::getId).toList());
        }
        return tasks.size();
    }

    private void sendTaskMessage(Long taskId) {
        try {
            redisMQTemplate.send(DownloadTaskMessage.builder().taskId(taskId).build());
        } catch (Exception e) {
            // 重投失败等下一轮扫描，不中断本轮其余清理
            log.error("[sendTaskMessage][补偿重投失败 taskId({})]", taskId, e);
        }
    }

    private boolean deleteStorageFileQuietly(DownloadTaskDO task) {
        try {
            FileClient client = fileConfigService.getFileClient(task.getStorageConfigId());
            if (client == null) {
                // 存储配置已被删除，产物无从回收，直接置为已过期避免死循环
                log.warn("[deleteStorageFileQuietly][存储配置不存在，跳过产物删除 taskId({}) configId({})]",
                        task.getId(), task.getStorageConfigId());
                return true;
            }
            client.delete(task.getStoragePath());
            return true;
        } catch (Exception e) {
            // 删除失败不打断（可能网络抖动），保留成功状态等待下轮重试
            log.error("[deleteStorageFileQuietly][产物删除失败 taskId({}) path({})]", task.getId(), task.getStoragePath(), e);
            return false;
        }
    }

    /**
     * 兜底清理本地临时 zip：正常路径打包完成即删除，此处防御异常进程的遗留文件
     */
    private int cleanTempFiles() {
        // 系统临时目录规范化后锁定清理范围，删除目标必须落在该目录内
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!Files.isDirectory(tempDir)) {
            log.warn("[cleanTempFiles][临时目录不存在,跳过清理 tempDir={}]", tempDir);
            return 0;
        }
        File[] expiredFiles = tempDir.toFile().listFiles((dir, name) ->
                Strings.CS.startsWith(name, TEMP_FILE_PREFIX) && isExpired(new File(dir, name)));
        if (expiredFiles == null || expiredFiles.length == 0) {
            return 0;
        }
        int deleted = 0;
        for (File file : expiredFiles) {
            if (!isInsideDir(file, tempDir)) {
                log.warn("[cleanTempFiles][待删除文件越出临时目录,跳过 file={}]", file.getAbsolutePath());
                continue;
            }
            // 删除失败不打断（可能正被上传占用），等下轮再清
            if (file.delete()) {
                deleted++;
            } else {
                log.warn("[cleanTempFiles][临时 zip 删除失败,等待下轮清理 file={}]", file.getAbsolutePath());
            }
        }
        return deleted;
    }

    private boolean isExpired(File file) {
        long expiredAt = System.currentTimeMillis() - TEMP_RETAIN_MINUTES * 60 * 1000;
        // listFiles 遍历期间文件可能被并发删除，lastModified 返回 0 时视为已不存在、跳过
        return file.exists() && file.lastModified() > 0 && file.lastModified() < expiredAt;
    }

    /**
     * 校验待删除文件的规范化路径仍位于临时目录内（防御 listFiles 结果被符号链接等带出边界）
     */
    private boolean isInsideDir(File file, Path tempDir) {
        try {
            return file.toPath().toAbsolutePath().normalize().startsWith(tempDir);
        } catch (SecurityException e) {
            return false;
        }
    }

}
