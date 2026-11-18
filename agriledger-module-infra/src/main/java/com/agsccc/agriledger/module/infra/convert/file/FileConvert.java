package com.agsccc.agriledger.module.infra.convert.file;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.file.vo.file.FileRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 文件 Convert
 *
 * @author Sail
 */
@Mapper
public interface FileConvert {

    FileConvert INSTANCE = Mappers.getMapper(FileConvert.class);

    FileDO convert(FileCreateReqVO bean);

    FileRespVO convert(FileDO bean);

    List<FileRespVO> convertList(List<FileDO> list);

    default PageResult<FileRespVO> convertPage(PageResult<FileDO> pageResult) {
        return new PageResult<>(convertList(pageResult.getList()), pageResult.getTotal());
    }

}
