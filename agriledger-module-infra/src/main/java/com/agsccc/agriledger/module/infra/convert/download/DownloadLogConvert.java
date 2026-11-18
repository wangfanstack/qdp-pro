package com.agsccc.agriledger.module.infra.convert.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadLogRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 下载中心审计日志 Convert
 *
 * @author Sail
 */
@Mapper
public interface DownloadLogConvert {

    DownloadLogConvert INSTANCE = Mappers.getMapper(DownloadLogConvert.class);

    DownloadLogRespVO convert(DownloadLogDO bean);

    List<DownloadLogRespVO> convertList(List<DownloadLogDO> list);

    default PageResult<DownloadLogRespVO> convertPage(PageResult<DownloadLogDO> pageResult) {
        return new PageResult<>(convertList(pageResult.getList()), pageResult.getTotal());
    }

}
