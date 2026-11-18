package com.agsccc.agriledger.module.infra.enums.download;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 下载中心审计动作的枚举
 *
 * @author Sail
 */
@Getter
@AllArgsConstructor
public enum DownloadLogActionEnum {

    /**
     * 创建下载任务
     */
    CREATE_TASK("CREATE_TASK", "创建下载任务"),
    /**
     * 取消下载任务
     */
    CANCEL("CANCEL", "取消下载任务"),
    /**
     * 删除下载任务
     */
    DELETE("DELETE", "删除下载任务"),
    /**
     * 获取任务产物下载直链
     */
    GET_TASK_URL("GET_TASK_URL", "获取任务下载直链"),
    /**
     * 服务端代理下载任务产物
     */
    DOWNLOAD_PROXY("DOWNLOAD_PROXY", "代理下载任务产物"),
    /**
     * 获取单文件下载直链
     */
    GET_FILE_URL("GET_FILE_URL", "获取单文件下载直链");

    /**
     * 动作编码（入库值）
     */
    private final String action;
    /**
     * 动作名（展示用）
     */
    private final String name;

}
