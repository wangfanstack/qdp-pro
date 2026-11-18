package com.agsccc.agriledger.module.system.dal.mysql.account;

import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局账号 Mapper
 *
 * @author 农E链源码
 */
@Mapper
public interface SystemAccountMapper extends BaseMapperX<SystemAccountDO> {

    default SystemAccountDO selectByMobile(String mobile) {
        return selectOne(SystemAccountDO::getMobile, mobile);
    }

}
