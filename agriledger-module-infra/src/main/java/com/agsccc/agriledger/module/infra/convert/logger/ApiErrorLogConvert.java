package com.agsccc.agriledger.module.infra.convert.logger;

import com.agsccc.agriledger.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.logger.ApiErrorLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * API 错误日志 Convert
 *
 * @author Sail
 */
@Mapper
public interface ApiErrorLogConvert {

    ApiErrorLogConvert INSTANCE = Mappers.getMapper(ApiErrorLogConvert.class);

    ApiErrorLogDO convert(ApiErrorLogCreateReqDTO bean);

    ApiErrorLogRespVO convert(ApiErrorLogDO bean);

    List<ApiErrorLogRespVO> convertList(List<ApiErrorLogDO> list);

    default PageResult<ApiErrorLogRespVO> convertPage(PageResult<ApiErrorLogDO> pageResult) {
        return new PageResult<>(convertList(pageResult.getList()), pageResult.getTotal());
    }

}
