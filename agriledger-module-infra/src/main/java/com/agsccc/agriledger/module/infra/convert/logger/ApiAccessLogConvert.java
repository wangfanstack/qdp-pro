package com.agsccc.agriledger.module.infra.convert.logger;

import com.agsccc.agriledger.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.logger.ApiAccessLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * API 访问日志 Convert
 *
 * @author Sail
 */
@Mapper
public interface ApiAccessLogConvert {

    ApiAccessLogConvert INSTANCE = Mappers.getMapper(ApiAccessLogConvert.class);

    ApiAccessLogDO convert(ApiAccessLogCreateReqDTO bean);

    ApiAccessLogRespVO convert(ApiAccessLogDO bean);

    List<ApiAccessLogRespVO> convertList(List<ApiAccessLogDO> list);

    default PageResult<ApiAccessLogRespVO> convertPage(PageResult<ApiAccessLogDO> pageResult) {
        return new PageResult<>(convertList(pageResult.getList()), pageResult.getTotal());
    }

}
