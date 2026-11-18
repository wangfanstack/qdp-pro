package com.agsccc.agriledger.module.infra.service.download;

import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import com.agsccc.agriledger.module.infra.enums.download.DownloadTaskTypeEnum;

/**
 * 下载任务处理器 SPI
 *
 * <p>下载中心按任务类型（{@link DownloadTaskTypeEnum}）分发到对应处理器执行。
 * 文件 ZIP 打包由 infra 内置实现（FileZipDownloadTaskHandler）；
 * Excel 导出等业务任务由业务模块实现本接口并以 Spring Bean 注册即可接入，
 * 共用下载中心的任务排队、进度、取消、过期与审计能力。
 *
 * <p>实现注意：处理器在任务已 CAS 抢占（PROCESSING）后被调用，执行期间应通过
 * 进度更新维持心跳，并尊重用户取消（检测到取消即中止清理）。
 *
 * @author Sail
 */
public interface DownloadTaskHandler {

    /**
     * 处理的任务类型
     *
     * @return 类型编号，参见 {@link DownloadTaskTypeEnum}
     */
    Integer getType();

    /**
     * 执行任务：读取任务参数、生成产物并上传文件存储、登记终态
     * （成功登记 storage 信息与保留期；失败登记错误信息）
     *
     * @param task 下载任务（已处于 PROCESSING 状态）
     */
    void executeTask(DownloadTaskDO task);

}
