package com.agsccc.agriledger.module.system.convert.auth;

import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthTenantRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * APP 认证 Convert
 *
 * @author 农E链源码
 */
@Mapper
public interface AppAuthConvert {

    AppAuthConvert INSTANCE = Mappers.getMapper(AppAuthConvert.class);

    @Mapping(target = "accountId", ignore = true) // 由 Service 按全局账号补充
    AppAuthLoginRespVO convert(OAuth2AccessTokenDO bean);

    @Mapping(source = "id", target = "tenantId")
    AppAuthTenantRespVO convert(TenantDO bean);

    List<AppAuthTenantRespVO> convertList(List<TenantDO> list);

    @Mapping(source = "scene", target = "scene")
    @Mapping(source = "usedIp", target = "usedIp")
    SmsCodeUseReqDTO convert(AppAuthSmsLoginReqVO reqVO, Integer scene, String usedIp);

}
