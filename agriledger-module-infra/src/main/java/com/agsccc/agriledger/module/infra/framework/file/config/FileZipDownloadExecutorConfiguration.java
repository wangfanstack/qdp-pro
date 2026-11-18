package com.agsccc.agriledger.module.infra.framework.file.config;

import com.agsccc.agriledger.module.infra.framework.download.config.DownloadTaskProperties;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 下载中心打包执行线程池配置
 *
 * <p>打包任务由 MQ 消费者（DownloadTaskConsumer）DB CAS 抢占（QUEUED→PROCESSING）后
 * 提交到本池执行，消费线程立即返回不承载长任务。池大小即单实例最大并行打包数
 * （打包为逐文件流式 IO，内存占用与文件规模无关，并发主要受存储带宽约束）。
 *
 * <p>拒绝与补偿语义：队列有界 + AbortPolicy，池满提交抛 RejectedExecutionException，
 * 消费链路将任务 CAS 退回排队态（PROCESSING→QUEUED），由 DownloadTaskCleanJob 的
 * 排队停滞扫描补偿重投——绝不使用 CallerRunsPolicy（那会让 MQ 消费线程代为执行
 * 分钟级 ZIP 打包，阻塞整个 Stream 消费）。
 *
 * <p>优雅停机：等待在途任务最长 awaitTerminationSeconds（GB 级打包可能超时未完），
 * 超时被中断的任务停留在打包态，由心跳停滞恢复机制重排队，不丢失。
 *
 * <p>本池会被 hft-spring-boot-starter-job 的 TTL 后置处理器按类型自动装饰
 * （TtlRunnable::get），跨线程传递租户/安全上下文；任务执行同时以任务记录的
 * 租户快照显式恢复上下文，不依赖消费线程的瞬时上下文。
 *
 * @author Sail
 */
@Configuration(proxyBeanMethods = false)
public class FileZipDownloadExecutorConfiguration {

    @Resource
    private DownloadTaskProperties downloadTaskProperties;

    /**
     * 打包执行线程池：参数由 hft.download.executor.* 配置（默认 2/2/20），
     * 池满即拒绝并回退任务排队态，等待补偿重投
     */
    @Bean
    public ThreadPoolTaskExecutor fileZipDownloadExecutor() {
        DownloadTaskProperties.Executor executorProperties = downloadTaskProperties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("file-zip-worker-");
        executor.setCorePoolSize(executorProperties.getCorePoolSize());
        executor.setMaxPoolSize(executorProperties.getMaxPoolSize());
        executor.setQueueCapacity(executorProperties.getQueueCapacity());
        // 池满拒绝：由消费链路 CAS 退回排队态 + 补偿重投，禁止 CallerRuns 阻塞 MQ 消费线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 核心线程常驻（打包任务低频大粒度，按需回收线程无收益）
        executor.setAllowCoreThreadTimeOut(false);
        // 优雅停机：等待在途打包完成，超时未完成的由停滞恢复机制重排队兜底
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(executorProperties.getAwaitTerminationSeconds());
        return executor;
    }

}
