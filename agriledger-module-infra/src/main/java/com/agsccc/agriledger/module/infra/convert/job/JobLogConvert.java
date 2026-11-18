package com.agsccc.agriledger.module.infra.convert.job;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.job.vo.log.JobLogRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.job.JobLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 定时任务日志 Convert
 *
 * @author Sail
 */
@Mapper
public interface JobLogConvert {

    JobLogConvert INSTANCE = Mappers.getMapper(JobLogConvert.class);

    JobLogRespVO convert(JobLogDO bean);

    List<JobLogRespVO> convertList(List<JobLogDO> list);

    default PageResult<JobLogRespVO> convertPage(PageResult<JobLogDO> pageResult) {
        return new PageResult<>(convertList(pageResult.getList()), pageResult.getTotal());
    }

}
