package com.agsccc.agriledger.module.infra.service.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadLogPageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadLogDO;
import com.agsccc.agriledger.module.infra.dal.mysql.download.DownloadLogMapper;
import com.agsccc.agriledger.module.infra.enums.download.DownloadLogActionEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.agsccc.agriledger.framework.common.util.servlet.ServletUtils.getClientIP;
import static com.agsccc.agriledger.framework.common.util.servlet.ServletUtils.getUserAgent;

/**
 * 下载中心审计日志 Service 实现类
 *
 * <p>记录下载相关操作的发起者与来源（谁在何时从哪个 IP 发起了下载）。
 * 日志写入失败不阻断业务（仅告警），审计属旁路能力。
 *
 * @author Sail
 */
@Service
@Slf4j
public class DownloadLogServiceImpl implements DownloadLogService {

    private final DownloadLogMapper downloadLogMapper;

    public DownloadLogServiceImpl(DownloadLogMapper downloadLogMapper) {
        this.downloadLogMapper = downloadLogMapper;
    }

    @Override
    public void record(DownloadLogActionEnum action, Long taskId, Long fileId, HttpServletRequest request) {
        try {
            DownloadLogDO logDO = DownloadLogDO.builder()
                    .taskId(taskId)
                    .fileId(fileId)
                    .action(action.getAction())
                    .clientIp(request != null ? getClientIP(request) : null)
                    .userAgent(request != null ? getUserAgent(request) : null)
                    .build();
            downloadLogMapper.insert(logDO);
        } catch (Exception e) {
            // 审计失败不影响业务主流程
            log.error("[record][下载审计记录失败 action({}) taskId({}) fileId({})]", action, taskId, fileId, e);
        }
    }

    @Override
    public PageResult<DownloadLogDO> getDownloadLogPage(DownloadLogPageReqVO pageReqVO) {
        return downloadLogMapper.selectPage(pageReqVO);
    }

}
