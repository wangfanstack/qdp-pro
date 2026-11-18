package com.agsccc.agriledger.module.infra.framework.download.config;

import com.agsccc.agriledger.module.infra.framework.file.core.utils.FileZipUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 下载中心相关的配置项
 *
 * <p>任务规模、保留期与重试上限属于业务保护阈值而非技术硬限制，
 * 按压测与运营情况调整，防止误操作拖垮存储与打包线程。
 *
 * @author Sail
 */
@Component
@ConfigurationProperties("hft.download")
@Data
public class DownloadTaskProperties {

    /**
     * 单任务文件数量上限（个）
     */
    private int maxFileCount = 1000;
    /**
     * 单任务总大小上限（字节，默认 20GB）
     */
    private long maxTaskSize = 20L * 1024 * 1024 * 1024;
    /**
     * 单用户排队中+打包中任务并发上限（个），防单用户垄断打包资源
     */
    private int maxUserProcessingTasks = 3;
    /**
     * 打包产物保留时长（天），到期由清理任务回收
     */
    private int retentionDays = 7;
    /**
     * 预签名下载直链有效期（秒，建议 5~10 分钟）
     */
    private int urlExpireSeconds = 600;
    /**
     * 消费者崩溃后的最大重排队次数，超过置为任务失败
     */
    private int maxRetryCount = 3;
    /**
     * 排队中任务的停滞判定时长（分钟）：超过视为消息丢失，补偿重投
     */
    private int queuedStaleMinutes = 5;
    /**
     * 打包中任务的心跳停滞判定时长（分钟）：超过视为执行进程中断
     */
    private int processingStaleMinutes = 30;
    /**
     * 打包时不做二次压缩的扩展名集合（已高度压缩类型，DEFLATE 收益极小且白耗 CPU）
     */
    private Set<String> zipNoCompressExtensions = FileZipUtils.DEFAULT_NO_COMPRESS_EXTENSIONS;
    /**
     * 打包执行线程池配置
     */
    private Executor executor = new Executor();

    /**
     * 打包执行线程池的配置项
     *
     * <p>池满即拒绝（AbortPolicy），任务由消费链路退回排队态等待补偿重投，
     * MQ 消费线程绝不代为执行长任务。
     */
    @Data
    public static class Executor {

        /**
         * 核心线程数（即单实例最大并行打包数，打包为逐文件流式 IO，并发主要受存储带宽约束）
         */
        private int corePoolSize = 2;
        /**
         * 最大线程数（与核心线程数保持一致，避免按需扩容打破并发上限语义）
         */
        private int maxPoolSize = 2;
        /**
         * 有界队列容量：缓冲排队任务，超出即拒绝
         */
        private int queueCapacity = 20;
        /**
         * 优雅停机等待时长（秒）：超时未完成的任务由停滞恢复机制重排队，不丢失
         */
        private int awaitTerminationSeconds = 60;

    }

}
