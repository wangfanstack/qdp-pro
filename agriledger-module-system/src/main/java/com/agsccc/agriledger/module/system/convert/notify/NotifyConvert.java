package com.agsccc.agriledger.module.system.convert.notify;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.controller.admin.notify.vo.message.NotifyMessageRespVO;
import com.agsccc.agriledger.module.system.controller.admin.notify.vo.template.NotifyTemplateRespVO;
import com.agsccc.agriledger.module.system.controller.admin.notify.vo.template.NotifyTemplateSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.notify.vo.template.NotifyTemplateSimpleRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.notify.NotifyMessageDO;
import com.agsccc.agriledger.module.system.dal.dataobject.notify.NotifyTemplateDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface NotifyConvert {

    NotifyConvert INSTANCE = Mappers.getMapper(NotifyConvert.class);

    NotifyTemplateRespVO convertToTemplateRespVO(NotifyTemplateDO bean);

    List<NotifyTemplateRespVO> convertTemplateRespVOList(List<NotifyTemplateDO> list);

    default PageResult<NotifyTemplateRespVO> convertTemplateRespVOPage(PageResult<NotifyTemplateDO> page) {
        return new PageResult<>(convertTemplateRespVOList(page.getList()), page.getTotal());
    }

    List<NotifyTemplateSimpleRespVO> convertTemplateSimpleRespVOList(List<NotifyTemplateDO> list);

    NotifyTemplateDO convertToTemplateDO(NotifyTemplateSaveReqVO bean);

    NotifyMessageRespVO convertToMessageRespVO(NotifyMessageDO bean);

    List<NotifyMessageRespVO> convertMessageRespVOList(List<NotifyMessageDO> list);

    default PageResult<NotifyMessageRespVO> convertMessageRespVOPage(PageResult<NotifyMessageDO> page) {
        return new PageResult<>(convertMessageRespVOList(page.getList()), page.getTotal());
    }

}
