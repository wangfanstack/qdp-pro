package com.agsccc.agriledger.module.system.dal.mysql.account;

import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 全局账号与租户管理用户关联 Mapper
 *
 * @author 农E链源码
 */
@Mapper
public interface SystemAccountUserRelMapper extends BaseMapperX<SystemAccountUserRelDO> {

    default SystemAccountUserRelDO selectByAccountIdAndTenantId(Long accountId, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<SystemAccountUserRelDO>()
                .eq(SystemAccountUserRelDO::getAccountId, accountId)
                .eq(SystemAccountUserRelDO::getTenantId, tenantId));
    }

    default SystemAccountUserRelDO selectBySystemUserId(Long systemUserId) {
        return selectOne(SystemAccountUserRelDO::getSystemUserId, systemUserId);
    }

    default List<SystemAccountUserRelDO> selectListByAccountId(Long accountId) {
        return selectList(new LambdaQueryWrapperX<SystemAccountUserRelDO>()
                .eq(SystemAccountUserRelDO::getAccountId, accountId)
                .orderByAsc(SystemAccountUserRelDO::getId));
    }

    default SystemAccountUserRelDO selectFirstByTenantId(Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<SystemAccountUserRelDO>()
                .eq(SystemAccountUserRelDO::getTenantId, tenantId)
                .orderByAsc(SystemAccountUserRelDO::getId)
                .last("LIMIT 1"));
    }

}
