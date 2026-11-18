package com.agsccc.agriledger.module.system.service.logger;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.api.logger.dto.LoginLogCreateReqDTO;
import com.agsccc.agriledger.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO;
import com.agsccc.agriledger.module.system.convert.logger.OperateLogConvert;
import com.agsccc.agriledger.module.system.dal.dataobject.logger.LoginLogDO;
import com.agsccc.agriledger.module.system.dal.mysql.logger.LoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

/**
 * 登录日志 Service 实现
 */
@Service
@Validated
public class LoginLogServiceImpl implements LoginLogService {

    @Resource
    private LoginLogMapper loginLogMapper;

    @Override
    public LoginLogDO getLoginLog(Long id) {
        return loginLogMapper.selectById(id);
    }

    @Override
    public PageResult<LoginLogDO> getLoginLogPage(LoginLogPageReqVO pageReqVO) {
        return loginLogMapper.selectPage(pageReqVO);
    }

    @Override
    public void createLoginLog(LoginLogCreateReqDTO reqDTO) {
        LoginLogDO loginLog = OperateLogConvert.INSTANCE.convertToLoginLogDO(reqDTO);
        loginLogMapper.insert(loginLog);
    }

}
