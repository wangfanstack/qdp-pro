package com.agsccc.agriledger.module.system.convert.permission;

import com.agsccc.agriledger.module.system.controller.admin.permission.vo.menu.MenuRespVO;
import com.agsccc.agriledger.module.system.controller.admin.permission.vo.menu.MenuSaveVO;
import com.agsccc.agriledger.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.permission.MenuDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface MenuConvert {

    MenuConvert INSTANCE = Mappers.getMapper(MenuConvert.class);

    MenuDO convertToDO(MenuSaveVO bean);

    MenuRespVO convertToRespVO(MenuDO bean);

    List<MenuRespVO> convertRespVOList(List<MenuDO> list);

    List<MenuSimpleRespVO> convertSimpleRespVOList(List<MenuDO> list);

}
