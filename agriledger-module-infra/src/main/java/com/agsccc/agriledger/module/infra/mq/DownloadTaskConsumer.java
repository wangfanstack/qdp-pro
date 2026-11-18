package com.agsccc.agriledger.module.infra.mq;

import com.agsccc.agriledger.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener;
import com.agsccc.agriledger.module.infra.mq.DownloadTaskMessage;
import com.agsccc.agriledger.module.infra.service.download.DownloadTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 下载任务执行消息消费者
 *
 * <p>继承 {@link AbstractRedisStreamMessageListener}，由框架自动注册为 Redis Stream
 * 集群消费者（消费者组），消费成功自动 ack，进程崩溃后由
 * {@code RedisPendingMessageResendJob} 兜底重投。
 *
 * <p>本消费者只做抢占与派发：CAS 排队中→打包中获得执行权（多实例/重复投递下仅一个
 * 消费者成功），随后提交打包执行线程池异步执行，监听线程立即返回不阻塞后续消息；
 * 执行池满时任务退回排队态，由清理任务补偿重投。
 *
 * @author Sail
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadTaskConsumer extends AbstractRedisStreamMessageListener<DownloadTaskMessage> {

    private final DownloadTaskService downloadTaskService;

    @Override
    public void onMessage(DownloadTaskMessage message) {
        log.info("[onMessage][消费下载任务消息 taskId({})]", message.getTaskId());
        downloadTaskService.executeQueuedTask(message.getTaskId());
    }

}
