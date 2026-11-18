package com.agsccc.agriledger.module.infra.convert.errorcode;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodeRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodeSaveReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.errorcode.ErrorCodeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ErrorCodeConvert {

    ErrorCodeConvert INSTANCE = Mappers.getMapper(ErrorCodeConvert.class);

    ErrorCodeDO convert(ErrorCodeSaveReqVO bean);

    ErrorCodeRespVO convert(ErrorCodeDO bean);

    PageResult<ErrorCodeRespVO> convertPage(PageResult<ErrorCodeDO> page);

}
