package com.agsccc.agriledger.module.system.convert.dict;

import com.agsccc.agriledger.framework.common.biz.system.dict.dto.DictDataRespDTO;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.controller.admin.dict.vo.data.DictDataRespVO;
import com.agsccc.agriledger.module.system.controller.admin.dict.vo.data.DictDataSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.dict.vo.data.DictDataSimpleRespVO;
import com.agsccc.agriledger.module.system.controller.admin.dict.vo.type.DictTypeRespVO;
import com.agsccc.agriledger.module.system.controller.admin.dict.vo.type.DictTypeSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.dict.vo.type.DictTypeSimpleRespVO;
import com.agsccc.agriledger.module.system.controller.app.dict.vo.AppDictDataRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.dict.DictDataDO;
import com.agsccc.agriledger.module.system.dal.dataobject.dict.DictTypeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface DictDataConvert {

    DictDataConvert INSTANCE = Mappers.getMapper(DictDataConvert.class);

    DictDataRespDTO convertToRespDTO(DictDataDO bean);

    List<DictDataRespDTO> convertRespDTOList(List<DictDataDO> list);

    DictDataDO convertToDataDO(DictDataSaveReqVO bean);

    DictDataRespVO convertToDataRespVO(DictDataDO bean);

    List<DictDataRespVO> convertDataRespVOList(List<DictDataDO> list);

    default PageResult<DictDataRespVO> convertDataRespVOPage(PageResult<DictDataDO> page) {
        return new PageResult<>(convertDataRespVOList(page.getList()), page.getTotal());
    }

    AppDictDataRespVO convertToAppRespVO(DictDataDO bean);

    List<AppDictDataRespVO> convertAppRespVOList(List<DictDataDO> list);

    DictTypeDO convertToTypeDO(DictTypeSaveReqVO bean);

    DictTypeRespVO convertToTypeRespVO(DictTypeDO bean);

    List<DictTypeRespVO> convertTypeRespVOList(List<DictTypeDO> list);

    default PageResult<DictTypeRespVO> convertTypeRespVOPage(PageResult<DictTypeDO> page) {
        return new PageResult<>(convertTypeRespVOList(page.getList()), page.getTotal());
    }

    List<DictDataSimpleRespVO> convertDataSimpleRespVOList(List<DictDataDO> list);

    List<DictTypeSimpleRespVO> convertTypeSimpleRespVOList(List<DictTypeDO> list);

}
