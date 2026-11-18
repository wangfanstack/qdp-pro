package com.agsccc.agriledger.module.system.convert.permission;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.api.permission.dto.RoleRespDTO;
import com.agsccc.agriledger.module.system.controller.admin.permission.vo.role.RoleRespVO;
import com.agsccc.agriledger.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.permission.RoleDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RoleConvert {

    RoleConvert INSTANCE = Mappers.getMapper(RoleConvert.class);

    RoleRespDTO convertToRespDTO(RoleDO bean);

    List<RoleRespDTO> convertRespDTOList(List<RoleDO> list);

    RoleDO convertToDO(RoleSaveReqVO bean);

    RoleSaveReqVO convertToSaveReqVO(RoleDO bean);

    RoleRespVO convertToRespVO(RoleDO bean);

    List<RoleRespVO> convertRespVOList(List<RoleDO> list);

    default PageResult<RoleRespVO> convertRespVOPage(PageResult<RoleDO> page) {
        return new PageResult<>(convertRespVOList(page.getList()), page.getTotal());
    }

}
