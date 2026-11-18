package com.agsccc.agriledger.module.infra.mq;

import com.agsccc.agriledger.framework.mq.redis.core.stream.AbstractRedisStreamMessage;
import lombok.*;

/**
 * 下载任务执行消息
 *
 * <p>消息只携带任务编号（MySQL 为唯一事实来源，消费者按编号重载任务与文件清单）。
 * 由 DownloadTaskCleanJob 对停滞任务补偿重投；重复投递由消费端 CAS 抢占幂等吸收。
 *
 * @author Sail
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class DownloadTaskMessage extends AbstractRedisStreamMessage {

    /**
     * 下载任务编号
     */
    private Long taskId;

}
