package com.agsccc.agriledger.module.system.dal.mysql.mail;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.template.MailTemplatePageReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.mail.MailTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MailTemplateMapper extends BaseMapperX<MailTemplateDO> {

    default PageResult<MailTemplateDO> selectPage(MailTemplatePageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<MailTemplateDO>()
                .eqIfPresent(MailTemplateDO::getStatus, pageReqVO.getStatus())
                .likeIfPresent(MailTemplateDO::getCode, pageReqVO.getCode())
                .likeIfPresent(MailTemplateDO::getName, pageReqVO.getName())
                .eqIfPresent(MailTemplateDO::getAccountId, pageReqVO.getAccountId())
                .betweenIfPresent(MailTemplateDO::getCreateTime, pageReqVO.getCreateTime())
                .orderByDesc(MailTemplateDO::getId));
    }

    default Long selectCountByAccountId(Long accountId) {
        return selectCount(MailTemplateDO::getAccountId, accountId);
    }

    default MailTemplateDO selectByCode(String code) {
        return selectOne(MailTemplateDO::getCode, code);
    }

    default List<MailTemplateDO> selectListByStatus(Integer status) {
        return selectList(MailTemplateDO::getStatus, status);
    }

}
