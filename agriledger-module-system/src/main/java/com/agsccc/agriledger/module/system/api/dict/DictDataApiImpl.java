package com.agsccc.agriledger.module.system.api.dict;

import com.agsccc.agriledger.framework.common.biz.system.dict.dto.DictDataRespDTO;
import com.agsccc.agriledger.module.system.convert.dict.DictDataConvert;
import com.agsccc.agriledger.module.system.dal.dataobject.dict.DictDataDO;
import com.agsccc.agriledger.module.system.service.dict.DictDataService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 字典数据 API 实现类
 *
 * @author 农E链源码
 */
@Service
public class DictDataApiImpl implements DictDataApi {

    @Resource
    private DictDataService dictDataService;

    @Override
    public void validateDictDataList(String dictType, Collection<String> values) {
        dictDataService.validateDictDataList(dictType, values);
    }

    @Override
    public List<DictDataRespDTO> getDictDataList(String dictType) {
        List<DictDataDO> list = dictDataService.getDictDataListByDictType(dictType);
        return DictDataConvert.INSTANCE.convertRespDTOList(list);
    }

}
