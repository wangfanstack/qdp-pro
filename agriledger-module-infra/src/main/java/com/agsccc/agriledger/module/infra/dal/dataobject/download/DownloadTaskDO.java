package com.agsccc.agriledger.module.infra.dal.dataobject.download;

import com.agsccc.agriledger.framework.mybatis.core.dataobject.BaseDO;
import com.agsccc.agriledger.framework.tenant.core.aop.TenantIgnore;
import com.agsccc.agriledger.module.infra.enums.download.DownloadTaskStatusEnum;
import com.agsccc.agriledger.module.infra.enums.download.DownloadTaskTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 下载中心任务表 DO
 *
 * <p>记录异步打包任务的进度与产物位置。文件能力跨租户共享
 * （与 {@link com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO} 一致），
 * 可见性与操作权限按 creator（用户编号全局唯一）隔离。
 *
 * @author Sail
 */
@TableName("infra_download_task")
@KeySequence("infra_download_task_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class DownloadTaskDO extends BaseDO {

    /**
     * 任务编号，数据库自增
     */
    @TableId
    private Long id;
    /**
     * 任务名称，即 zip 文件名，展示与下载时使用
     */
    private String name;
    /**
     * 任务类型
     *
     * 枚举 {@link DownloadTaskTypeEnum}
     */
    private Integer type;
    /**
     * 任务状态
     *
     * 枚举 {@link DownloadTaskStatusEnum}
     */
    private Integer status;
    /**
     * 幂等请求标识：同一创建者重复提交相同 requestId 时返回原任务
     */
    private String requestId;
    /**
     * 创建任务时的租户编号（快照）：异步执行时以此恢复租户上下文，
     * 不依赖 MQ 消费线程的瞬时上下文（重试/重启/跨实例补偿后执行依然正确）；
     * 仅作快照用途，任务表本身仍全局共享（@TenantIgnore），不参与租户过滤
     */
    private Long tenantId;
    /**
     * 任务文件编号清单（JSON 数组字符串）：执行时重新加载与校验，
     * 不依赖创建时的内存态（排队期间文件可能被删除）
     */
    private String fileIds;
    /**
     * 消费者崩溃后已重排队的次数
     */
    private Integer retryCount;
    /**
     * 待打包文件总数
     */
    private Integer totalCount;
    /**
     * 已处理完成文件数（含获取失败的）
     */
    private Integer finishedCount;
    /**
     * 获取失败的文件数
     */
    private Integer failedCount;
    /**
     * 待打包总字节数，进度分母
     */
    private Long totalBytes;
    /**
     * 已写出字节数，进度分子
     */
    private Long completedBytes;
    /**
     * 产物大小，单位字节，成功后有值
     */
    private Long fileSize;
    /**
     * 产物所在的文件配置编号
     */
    private Long storageConfigId;
    /**
     * 产物存储相对路径
     */
    private String storagePath;
    /**
     * 产物上传时返回的访问地址，仅记录用途，下载统一走存储流式读取
     */
    private String storageUrl;
    /**
     * 失败原因或失效文件提示
     */
    private String errorMessage;
    /**
     * 产物过期时间，到期后由清理任务回收
     */
    private LocalDateTime expiredTime;
    /**
     * 打包完成时间，成功时有值
     */
    private LocalDateTime completedTime;

}
