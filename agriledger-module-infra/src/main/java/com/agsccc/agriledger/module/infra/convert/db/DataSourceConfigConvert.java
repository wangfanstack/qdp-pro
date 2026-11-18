package com.agsccc.agriledger.module.infra.convert.db;

import com.agsccc.agriledger.module.infra.controller.admin.db.vo.DataSourceConfigRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.db.DataSourceConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 数据源配置 Convert
 *
 * @author Sail
 */
@Mapper
public interface DataSourceConfigConvert {

    DataSourceConfigConvert INSTANCE = Mappers.getMapper(DataSourceConfigConvert.class);

    DataSourceConfigDO convert(DataSourceConfigSaveReqVO bean);

    DataSourceConfigRespVO convert(DataSourceConfigDO bean);

    List<DataSourceConfigRespVO> convertList(List<DataSourceConfigDO> list);

}
