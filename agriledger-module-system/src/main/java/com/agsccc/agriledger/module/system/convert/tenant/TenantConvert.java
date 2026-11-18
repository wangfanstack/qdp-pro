package com.agsccc.agriledger.module.system.convert.tenant;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.controller.admin.tenant.vo.packages.TenantPackageRespVO;
import com.agsccc.agriledger.module.system.controller.admin.tenant.vo.packages.TenantPackageSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.tenant.vo.packages.TenantPackageSimpleRespVO;
import com.agsccc.agriledger.module.system.controller.admin.tenant.vo.tenant.TenantRespVO;
import com.agsccc.agriledger.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import com.agsccc.agriledger.module.system.controller.app.tenant.vo.AppTenantRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantPackageDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 租户 Convert
 *
 * @author 农E链源码
 */
@Mapper
public interface TenantConvert {

    TenantConvert INSTANCE = Mappers.getMapper(TenantConvert.class);

    default UserSaveReqVO convert02(TenantSaveReqVO bean) {
        UserSaveReqVO reqVO = new UserSaveReqVO();
        reqVO.setUsername(bean.getUsername());
        reqVO.setPassword(bean.getPassword());
        reqVO.setNickname(bean.getContactName()).setMobile(bean.getContactMobile());
        return reqVO;
    }

    TenantDO convertToDO(TenantSaveReqVO bean);

    TenantRespVO convertToRespVO(TenantDO bean);

    List<TenantRespVO> convertRespVOList(List<TenantDO> list);

    default PageResult<TenantRespVO> convertRespVOPage(PageResult<TenantDO> page) {
        return new PageResult<>(convertRespVOList(page.getList()), page.getTotal());
    }

    TenantPackageDO convertToPackageDO(TenantPackageSaveReqVO bean);

    TenantPackageRespVO convertToPackageRespVO(TenantPackageDO bean);

    List<TenantPackageRespVO> convertPackageRespVOList(List<TenantPackageDO> list);

    default PageResult<TenantPackageRespVO> convertPackageRespVOPage(PageResult<TenantPackageDO> page) {
        return new PageResult<>(convertPackageRespVOList(page.getList()), page.getTotal());
    }

    TenantPackageSimpleRespVO convertToPackageSimpleRespVO(TenantPackageDO bean);

    List<TenantPackageSimpleRespVO> convertPackageSimpleRespVOList(List<TenantPackageDO> list);

    AppTenantRespVO convertToAppRespVO(TenantDO bean);

}
