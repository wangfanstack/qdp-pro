package com.agsccc.agriledger.module.system.api.account;

import com.agsccc.agriledger.framework.common.util.collection.CollectionUtils;
import com.agsccc.agriledger.module.system.api.account.dto.SystemAccountRespDTO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import com.agsccc.agriledger.module.system.service.account.SystemAccountService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 全局账号 API 实现类
 *
 * @author 农E链源码
 */
@Service
@Validated
public class SystemAccountApiImpl implements SystemAccountApi {

    @Resource
    private SystemAccountService systemAccountService;

    @Override
    public SystemAccountRespDTO getOrCreateAccountByMobile(String mobile) {
        SystemAccountDO account = systemAccountService.getOrCreateAccountByMobile(mobile);
        // 账号停用后不允许发起新的商户业务
        systemAccountService.validateAccountEnabled(account);
        return new SystemAccountRespDTO()
                .setId(account.getId())
                .setMobile(account.getMobile())
                .setStatus(account.getStatus());
    }

    @Override
    public Long getOwnerAccountIdByTenantId(Long tenantId) {
        return systemAccountService.getOwnerAccountIdByTenantId(tenantId);
    }

    @Override
    public List<Long> getValidTenantIdsByAccountId(Long accountId) {
        return CollectionUtils.convertList(
                systemAccountService.getValidRelListByAccountId(accountId),
                SystemAccountUserRelDO::getTenantId);
    }

    @Override
    public Long getRelSystemUserId(Long accountId, Long tenantId) {
        return systemAccountService.getValidRel(accountId, tenantId).getSystemUserId();
    }

}
