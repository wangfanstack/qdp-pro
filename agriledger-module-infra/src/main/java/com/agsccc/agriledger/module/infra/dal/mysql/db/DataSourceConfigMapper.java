package com.agsccc.agriledger.module.infra.dal.mysql.db;

import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.module.infra.dal.dataobject.db.DataSourceConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源配置 Mapper
 *
 * @author Sail
 */
@Mapper
public interface DataSourceConfigMapper extends BaseMapperX<DataSourceConfigDO> {
}
