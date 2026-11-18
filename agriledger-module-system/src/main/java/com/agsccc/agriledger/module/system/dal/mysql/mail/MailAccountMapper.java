package com.agsccc.agriledger.module.system.dal.mysql.mail;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.mail.MailAccountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailAccountMapper extends BaseMapperX<MailAccountDO> {

    default PageResult<MailAccountDO> selectPage(MailAccountPageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<MailAccountDO>()
                .likeIfPresent(MailAccountDO::getMail, pageReqVO.getMail())
                .likeIfPresent(MailAccountDO::getUsername, pageReqVO.getUsername())
                .orderByDesc(MailAccountDO::getId));
    }

}
