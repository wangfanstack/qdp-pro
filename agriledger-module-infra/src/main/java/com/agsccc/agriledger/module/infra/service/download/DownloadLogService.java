package com.agsccc.agriledger.module.infra.service.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadLogPageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadLogDO;
import com.agsccc.agriledger.module.infra.enums.download.DownloadLogActionEnum;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 下载中心审计日志 Service 接口
 *
 * @author Sail
 */
public interface DownloadLogService {

    /**
     * 记录下载相关操作日志（写入失败不阻断业务）
     *
     * @param action  动作
     * @param taskId  下载任务编号，单文件动作时为空
     * @param fileId  文件编号，任务级动作时为空
     * @param request 当前请求（取客户端 IP 与 User-Agent，允许空）
     */
    void record(DownloadLogActionEnum action, Long taskId, Long fileId, HttpServletRequest request);

    /**
     * 获得审计日志分页
     *
     * @param pageReqVO 分页查询
     * @return 审计日志分页
     */
    PageResult<DownloadLogDO> getDownloadLogPage(DownloadLogPageReqVO pageReqVO);

}
