package com.agsccc.agriledger.module.infra.convert.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 下载中心任务 Convert
 *
 * @author Sail
 */
@Mapper
public interface DownloadTaskConvert {

    DownloadTaskConvert INSTANCE = Mappers.getMapper(DownloadTaskConvert.class);

    DownloadTaskRespVO convert(DownloadTaskDO bean);

    List<DownloadTaskRespVO> convertList(List<DownloadTaskDO> list);

    default PageResult<DownloadTaskRespVO> convertPage(PageResult<DownloadTaskDO> pageResult) {
        return new PageResult<>(convertList(pageResult.getList()), pageResult.getTotal());
    }

}
