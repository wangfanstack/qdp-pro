package com.agsccc.agriledger.module.infra.dal.mysql.errorcode;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodePageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.errorcode.ErrorCodeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误码文案 Mapper
 *
 * @author Sail
 */
@Mapper
public interface ErrorCodeMapper extends BaseMapperX<ErrorCodeDO> {

    default ErrorCodeDO selectByCode(Integer code) {
        return selectOne(ErrorCodeDO::getCode, code);
    }

    default PageResult<ErrorCodeDO> selectPage(ErrorCodePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErrorCodeDO>()
                .eqIfPresent(ErrorCodeDO::getCode, reqVO.getCode())
                .likeIfPresent(ErrorCodeDO::getModule, reqVO.getModule())
                .likeIfPresent(ErrorCodeDO::getMessage, reqVO.getMessage())
                .orderByAsc(ErrorCodeDO::getCode));
    }

}
