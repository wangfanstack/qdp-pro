package com.agsccc.agriledger.module.infra.enums.download;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 下载任务类型的枚举
 *
 * <p>下载中心定位为通用异步产物任务，当前仅实现文件 ZIP 打包，
 * 后续 Excel 导出等场景可按类型扩展。
 *
 * @author Sail
 */
@Getter
@AllArgsConstructor
public enum DownloadTaskTypeEnum {

    /**
     * 文件 ZIP 打包
     */
    FILE_ZIP(10, "文件 ZIP 打包"),
    /**
     * Excel 导出（由业务模块实现 DownloadTaskHandler 接入后开放）
     */
    EXCEL(20, "Excel 导出");

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 类型名
     */
    private final String name;

}
