package com.agsccc.agriledger.module.system.convert.social;

import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.api.social.dto.SocialWxJsapiSignatureRespDTO;
import com.agsccc.agriledger.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import com.agsccc.agriledger.module.system.api.social.dto.SocialWxaSubscribeTemplateRespDTO;
import com.agsccc.agriledger.module.system.controller.admin.social.vo.client.SocialClientRespVO;
import com.agsccc.agriledger.module.system.controller.admin.social.vo.client.SocialClientSaveReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.social.SocialClientDO;
import me.chanjar.weixin.common.bean.WxJsapiSignature;
import me.chanjar.weixin.common.bean.subscribemsg.TemplateInfo;
import me.zhyd.oauth.config.AuthConfig;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface SocialClientConvert {

    SocialClientConvert INSTANCE = Mappers.getMapper(SocialClientConvert.class);

    SocialWxJsapiSignatureRespDTO convert(WxJsapiSignature bean);

    SocialWxPhoneNumberInfoRespDTO convert(WxMaPhoneNumberInfo bean);

    SocialWxaSubscribeTemplateRespDTO convert(TemplateInfo bean);

    SocialClientDO convertToDO(SocialClientSaveReqVO bean);

    SocialClientRespVO convertToRespVO(SocialClientDO bean);

    List<SocialClientRespVO> convertRespVOList(List<SocialClientDO> list);

    /**
     * 拷贝 JustAuth 配置（同类型逐属性浅拷贝），用于 {@link com.agsccc.agriledger.module.system.service.social.SocialClientServiceImpl}
     * 构建 DB 配置覆盖 clientId/clientSecret 用的新 AuthConfig 对象
     *
     * @param config 原配置
     * @return 新配置对象（入参为 null 时返回 null）
     */
    AuthConfig cloneAuthConfig(AuthConfig config);

    default PageResult<SocialClientRespVO> convertRespVOPage(PageResult<SocialClientDO> page) {
        return new PageResult<>(convertRespVOList(page.getList()), page.getTotal());
    }

}
