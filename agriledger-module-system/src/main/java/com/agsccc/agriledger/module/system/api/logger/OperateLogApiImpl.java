package com.agsccc.agriledger.module.system.api.logger;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.biz.system.logger.dto.OperateLogCreateReqDTO;
import com.agsccc.agriledger.module.system.api.logger.dto.OperateLogPageReqDTO;
import com.agsccc.agriledger.module.system.api.logger.dto.OperateLogRespDTO;
import com.agsccc.agriledger.module.system.convert.logger.OperateLogConvert;
import com.agsccc.agriledger.module.system.dal.dataobject.logger.OperateLogDO;
import com.agsccc.agriledger.module.system.service.logger.OperateLogService;
import org.dromara.core.trans.anno.TransMethodResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 操作日志 API 实现类
 *
 * @author 农E链源码
 */
@Service
@Validated
public class OperateLogApiImpl implements OperateLogApi {

    @Resource
    private OperateLogService operateLogService;

    @Override
    public void createOperateLog(OperateLogCreateReqDTO createReqDTO) {
        operateLogService.createOperateLog(createReqDTO);
    }

    @Override
    @TransMethodResult
    public PageResult<OperateLogRespDTO> getOperateLogPage(OperateLogPageReqDTO pageReqDTO) {
        PageResult<OperateLogDO> operateLogPage = operateLogService.getOperateLogPage(pageReqDTO);
        return OperateLogConvert.INSTANCE.convertRespDTOPage(operateLogPage);
    }

}
