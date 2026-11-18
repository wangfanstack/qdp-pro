package com.agsccc.agriledger.module.infra.service.download;

import com.agsccc.agriledger.framework.common.util.json.JsonUtils;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.agsccc.agriledger.module.infra.dal.mysql.download.DownloadTaskMapper;
import com.agsccc.agriledger.module.infra.framework.download.config.DownloadTaskProperties;
import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClient;
import com.agsccc.agriledger.module.infra.framework.file.core.enums.FileCategoryEnum;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FilePathUtils;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FileZipUtils;
import com.agsccc.agriledger.module.infra.service.download.DownloadTaskHandler;
import com.agsccc.agriledger.module.infra.service.file.FileConfigService;
import com.agsccc.agriledger.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipOutputStream;

import static com.agsccc.agriledger.module.infra.enums.download.DownloadTaskStatusEnum.CANCELLED;
import static com.agsccc.agriledger.module.infra.enums.download.DownloadTaskTypeEnum.FILE_ZIP;

/**
 * 文件 ZIP 打包任务处理器
 *
 * <p>执行流程：按任务记录重载文件清单（排队期间文件可能被删除，执行时重新校验）、
 * 逐文件流式写本地临时 zip（固定缓冲区，内存与文件规模无关）、上传产物到文件存储
 * 主配置、条件化登记终态。进度回调兼作心跳（update_time）与协作式取消检查点。
 *
 * @author Sail
 */
@Component
@Slf4j
public class FileZipDownloadTaskHandler implements DownloadTaskHandler {

    /**
     * 失败原因登记到任务表的最大长度，与 infra_download_task.error_message 列宽一致留余量
     */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 200;

    @Resource
    private DownloadTaskMapper downloadTaskMapper;
    @Resource
    private FileService fileService;
    @Resource
    private FileConfigService fileConfigService;
    @Resource
    private DownloadTaskProperties downloadTaskProperties;

    @Override
    public Integer getType() {
        return FILE_ZIP.getType();
    }

    @Override
    public void executeTask(DownloadTaskDO task) {
        Long taskId = task.getId();
        // 1. 按任务记录重载文件清单：不依赖创建时的内存态（排队期间文件可能被删除）
        List<Long> fileIds = JsonUtils.parseArray(task.getFileIds(), Long.class);
        if (CollectionUtils.isEmpty(fileIds)) {
            downloadTaskMapper.updateTaskFailedIfProcessing(taskId, "任务文件清单缺失");
            return;
        }
        List<FileDO> files = fileService.getFileList(fileIds);
        if (CollectionUtils.isEmpty(files)) {
            downloadTaskMapper.updateTaskFailedIfProcessing(taskId, "待打包文件已不存在");
            return;
        }
        // 2. 逐文件流式打包到本地临时 zip（内容不存在的文件立即中止：缺文件的 zip 风险高）
        File tempFile = null;
        try {
            // 前缀与 DownloadTaskCleanJob.TEMP_FILE_PREFIX 保持一致，供兜底清理识别
            tempFile = File.createTempFile("hft-file-zip-" + taskId + "-", ".zip");
            // 进程退出兜底清理；常规回收依赖 DownloadTaskCleanJob
            tempFile.deleteOnExit();
            AtomicInteger finishedCount = new AtomicInteger();
            AtomicLong completedBytes = new AtomicLong();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                FileZipUtils.writeZipEntries(files, zos,
                        file -> fileService.getFileContentStream(file.getConfigId(), file.getPath()),
                        new FileZipProgressListener(taskId, finishedCount, completedBytes),
                        true, downloadTaskProperties.getZipNoCompressExtensions());
            }
            // 3. 上传产物到文件存储主配置（流式），不登记 infra_file，避免污染文件管理列表
            FileClient client = fileConfigService.getMasterFileClient();
            String storagePath = buildStoragePath(task);
            File uploadFile = tempFile;
            String url = client.upload(() -> new FileInputStream(uploadFile), uploadFile.length(), storagePath, "application/zip");
            // 4. 条件化登记成功：失败说明任务已被并发取消，需回收已上传的产物
            boolean registered = downloadTaskMapper.updateTaskSuccessIfProcessing(taskId, uploadFile.length(),
                    client.getId(), storagePath, url,
                    LocalDateTime.now().plusDays(downloadTaskProperties.getRetentionDays()), LocalDateTime.now());
            if (!registered) {
                log.warn("[executeTask][任务已被取消,回收已上传产物 taskId({}) path({})]", taskId, storagePath);
                deleteStorageFileQuietly(client, storagePath);
                return;
            }
            log.info("[executeTask][打包完成 taskId({}) files({}) bytes({}) path({})]",
                    taskId, files.size(), uploadFile.length(), storagePath);
        } catch (DownloadTaskCancelledException e) {
            // 用户取消：状态已由 cancelDownloadTask 登记，这里仅中止并清理临时文件
            log.info("[executeTask][任务已取消,中止打包 taskId({})]", taskId);
        } catch (FileZipUtils.FileLoadException e) {
            // 首个失效文件即中止：明确提示失效文件，不静默跳过（公证法律文书缺件风险高）
            downloadTaskMapper.updateTaskFailedIfProcessing(taskId,
                    StringUtils.substring(e.getMessage(), 0, ERROR_MESSAGE_MAX_LENGTH));
            log.warn("[executeTask][文件获取失败,任务置为失败 taskId({}) cause({})]", taskId, e.getMessage());
        } catch (Exception e) {
            log.error("[executeTask][打包失败 taskId({})]", taskId, e);
            downloadTaskMapper.updateTaskFailedIfProcessing(taskId,
                    StringUtils.substring(e.getMessage(), 0, ERROR_MESSAGE_MAX_LENGTH));
        } finally {
            // 临时文件在上传完成后即无用途（产物已在文件存储），成功失败都立即回收
            if (tempFile != null && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }
    }

    /**
     * 进度回调：条目完成与条目内字节进展均更新任务表并检查取消，
     * 大文件（GB 级视频）拷贝期间每 32MB 维持一次心跳，防停滞恢复误判
     */
    private class FileZipProgressListener implements FileZipUtils.StreamProgressListener {

        private final Long taskId;
        private final AtomicInteger finishedCount;
        private final AtomicLong completedBytes;

        FileZipProgressListener(Long taskId, AtomicInteger finishedCount, AtomicLong completedBytes) {
            this.taskId = taskId;
            this.finishedCount = finishedCount;
            this.completedBytes = completedBytes;
        }

        @Override
        public void onEntryProcessed(FileDO file, boolean success, long bytesWritten) {
            // 字节数已由 onBytesWritten 全量累加（含条目收尾冲刷），此处仅推进文件数
            updateTaskProgress(taskId, finishedCount.incrementAndGet(), completedBytes.get());
            checkTaskCancelled(taskId);
        }

        @Override
        public void onBytesWritten(long deltaBytes) {
            updateTaskProgress(taskId, finishedCount.get(), completedBytes.addAndGet(deltaBytes));
            checkTaskCancelled(taskId);
        }

    }

    /**
     * 更新进度（同时刷新 update_time，兼作打包进程心跳，供清理任务判定任务中断）
     */
    private void updateTaskProgress(Long taskId, int finishedCount, long completedBytes) {
        DownloadTaskDO updateObj = new DownloadTaskDO();
        updateObj.setId(taskId);
        updateObj.setFinishedCount(finishedCount);
        updateObj.setCompletedBytes(completedBytes);
        downloadTaskMapper.updateById(updateObj);
    }

    /**
     * 检查任务是否已被用户取消，已取消则抛出中止信号
     */
    private void checkTaskCancelled(Long taskId) {
        DownloadTaskDO task = downloadTaskMapper.selectById(taskId);
        if (task != null && CANCELLED.getStatus().equals(task.getStatus())) {
            throw new DownloadTaskCancelledException();
        }
    }

    private void deleteStorageFileQuietly(FileClient client, String storagePath) {
        try {
            client.delete(storagePath);
        } catch (Exception e) {
            // 回收失败仅记录，残留对象由存储侧生命周期规则兜底
            log.error("[deleteStorageFileQuietly][产物回收失败 path({})]", storagePath, e);
        }
    }

    /**
     * 产物存储路径：export/{tenantId}/{yyyy}/{MM}/{uuid}.zip，按文件目录规范归入导出分类。
     * 任务名保留在任务记录中，路径 uuid 防覆盖，任务记录的 storage_path 提供映射
     */
    private String buildStoragePath(DownloadTaskDO task) {
        return FilePathUtils.buildStoragePath(FileCategoryEnum.EXPORT, task.getTenantId(), "zip");
    }

    /**
     * 任务被用户取消的中止信号（内部流转，不对外暴露）
     */
    static final class DownloadTaskCancelledException extends RuntimeException {
    }

}
