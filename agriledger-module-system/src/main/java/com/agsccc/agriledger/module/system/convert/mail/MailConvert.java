package com.agsccc.agriledger.module.system.convert.mail;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.account.MailAccountRespVO;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.account.MailAccountSimpleRespVO;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.log.MailLogRespVO;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.template.MailTemplateRespVO;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.template.MailTemplateSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.template.MailTemplateSimpleRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.mail.MailAccountDO;
import com.agsccc.agriledger.module.system.dal.dataobject.mail.MailLogDO;
import com.agsccc.agriledger.module.system.dal.dataobject.mail.MailTemplateDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface MailConvert {

    MailConvert INSTANCE = Mappers.getMapper(MailConvert.class);

    MailAccountRespVO convertToAccountRespVO(MailAccountDO bean);

    List<MailAccountRespVO> convertAccountRespVOList(List<MailAccountDO> list);

    default PageResult<MailAccountRespVO> convertAccountRespVOPage(PageResult<MailAccountDO> page) {
        return new PageResult<>(convertAccountRespVOList(page.getList()), page.getTotal());
    }

    List<MailAccountSimpleRespVO> convertAccountSimpleRespVOList(List<MailAccountDO> list);

    MailAccountDO convertToAccountDO(MailAccountSaveReqVO bean);

    MailTemplateRespVO convertToTemplateRespVO(MailTemplateDO bean);

    List<MailTemplateRespVO> convertTemplateRespVOList(List<MailTemplateDO> list);

    default PageResult<MailTemplateRespVO> convertTemplateRespVOPage(PageResult<MailTemplateDO> page) {
        return new PageResult<>(convertTemplateRespVOList(page.getList()), page.getTotal());
    }

    List<MailTemplateSimpleRespVO> convertTemplateSimpleRespVOList(List<MailTemplateDO> list);

    MailTemplateDO convertToTemplateDO(MailTemplateSaveReqVO bean);

    MailLogRespVO convertToLogRespVO(MailLogDO bean);

    List<MailLogRespVO> convertLogRespVOList(List<MailLogDO> list);

    default PageResult<MailLogRespVO> convertLogRespVOPage(PageResult<MailLogDO> page) {
        return new PageResult<>(convertLogRespVOList(page.getList()), page.getTotal());
    }

}
