package com.agsccc.agriledger.module.infra.dal.dataobject.download;

import com.agsccc.agriledger.framework.mybatis.core.dataobject.BaseDO;
import com.agsccc.agriledger.framework.tenant.core.aop.TenantIgnore;
import com.agsccc.agriledger.module.infra.enums.download.DownloadLogActionEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 下载中心审计日志表 DO
 *
 * <p>记录下载相关操作的发起者与来源（谁在何时从哪个 IP 发起了下载），
 * 满足合规追溯需求。文件能力跨租户共享（与 infra_file 一致），无 tenant_id。
 *
 * @author Sail
 */
@TableName("infra_download_log")
@KeySequence("infra_download_log_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class DownloadLogDO extends BaseDO {

    /**
     * 日志编号，数据库自增
     */
    @TableId
    private Long id;
    /**
     * 下载任务编号，单文件动作时为空
     */
    private Long taskId;
    /**
     * 文件编号，任务级动作时为空
     */
    private Long fileId;
    /**
     * 动作
     *
     * 枚举 {@link DownloadLogActionEnum}
     */
    private String action;
    /**
     * 客户端 IP
     */
    private String clientIp;
    /**
     * User-Agent
     */
    private String userAgent;

}
