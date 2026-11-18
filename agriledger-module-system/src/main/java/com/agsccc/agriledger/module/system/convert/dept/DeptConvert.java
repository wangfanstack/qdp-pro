package com.agsccc.agriledger.module.system.convert.dept;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.module.system.api.dept.dto.DeptRespDTO;
import com.agsccc.agriledger.module.system.api.dept.dto.PostRespDTO;
import com.agsccc.agriledger.module.system.controller.admin.dept.vo.dept.DeptRespVO;
import com.agsccc.agriledger.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.dept.vo.dept.DeptSimpleRespVO;
import com.agsccc.agriledger.module.system.controller.admin.dept.vo.post.PostRespVO;
import com.agsccc.agriledger.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.agsccc.agriledger.module.system.controller.admin.dept.vo.post.PostSimpleRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.dept.DeptDO;
import com.agsccc.agriledger.module.system.dal.dataobject.dept.PostDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface DeptConvert {

    DeptConvert INSTANCE = Mappers.getMapper(DeptConvert.class);

    DeptRespDTO convertToRespDTO(DeptDO bean);

    List<DeptRespDTO> convertRespDTOList(List<DeptDO> list);

    PostRespDTO convertToPostRespDTO(PostDO bean);

    List<PostRespDTO> convertPostRespDTOList(List<PostDO> list);

    DeptDO convertToDO(DeptSaveReqVO bean);

    PostDO convertToPostDO(PostSaveReqVO bean);

    DeptRespVO convertToRespVO(DeptDO bean);

    List<DeptRespVO> convertRespVOList(List<DeptDO> list);

    List<DeptSimpleRespVO> convertSimpleRespVOList(List<DeptDO> list);

    PostRespVO convertToPostRespVO(PostDO bean);

    List<PostRespVO> convertPostRespVOList(List<PostDO> list);

    default PageResult<PostRespVO> convertPostRespVOPage(PageResult<PostDO> page) {
        return new PageResult<>(convertPostRespVOList(page.getList()), page.getTotal());
    }

    List<PostSimpleRespVO> convertPostSimpleRespVOList(List<PostDO> list);

}
