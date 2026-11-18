package com.agsccc.agriledger.module.infra.dal.mysql.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadLogPageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 下载中心审计日志 Mapper
 *
 * @author Sail
 */
@Mapper
public interface DownloadLogMapper extends BaseMapperX<DownloadLogDO> {

    default PageResult<DownloadLogDO> selectPage(DownloadLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DownloadLogDO>()
                .eqIfPresent(DownloadLogDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(DownloadLogDO::getAction, reqVO.getAction())
                .betweenIfPresent(DownloadLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DownloadLogDO::getId));
    }

}
