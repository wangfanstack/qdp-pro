package com.agsccc.agriledger.module.system.convert.ip;

import com.agsccc.agriledger.framework.ip.core.Area;
import com.agsccc.agriledger.module.system.controller.admin.ip.vo.AreaNodeRespVO;
import com.agsccc.agriledger.module.system.controller.app.ip.vo.AppAreaNodeRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AreaConvert {

    AreaConvert INSTANCE = Mappers.getMapper(AreaConvert.class);

    AreaNodeRespVO convert(Area bean);

    List<AreaNodeRespVO> convertList(List<Area> list);

    AppAreaNodeRespVO convertApp(Area bean);

    List<AppAreaNodeRespVO> convertAppList(List<Area> list);

}
