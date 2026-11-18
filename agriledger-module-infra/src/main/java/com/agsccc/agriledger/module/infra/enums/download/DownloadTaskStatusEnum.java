package com.agsccc.agriledger.module.infra.enums.download;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 下载任务状态的枚举
 *
 * <p>状态机：QUEUED → PROCESSING → SUCCESS / FAILED；
 * PROCESSING 崩溃后按 retry_count 回到 QUEUED 重试或转 FAILED；
 * QUEUED / PROCESSING 可取消；SUCCESS 到期转 EXPIRED。
 *
 * @author Sail
 */
@Getter
@AllArgsConstructor
public enum DownloadTaskStatusEnum {

    /**
     * 排队中（已落库并投递消息，等待消费者抢占）
     */
    QUEUED(0, "排队中"),
    /**
     * 打包中（消费者已抢占执行权）
     */
    PROCESSING(1, "打包中"),
    /**
     * 打包成功（产物可下载）
     */
    SUCCESS(10, "打包成功"),
    /**
     * 打包失败
     */
    FAILED(20, "打包失败"),
    /**
     * 已过期（产物已被清理任务回收）
     */
    EXPIRED(30, "已过期"),
    /**
     * 已取消（用户主动取消，产物未生成）
     */
    CANCELLED(40, "已取消");

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 状态名
     */
    private final String name;

}
