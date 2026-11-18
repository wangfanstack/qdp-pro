package com.agsccc.agriledger.module.system.api.dept;

import com.agsccc.agriledger.module.system.api.dept.dto.PostRespDTO;
import com.agsccc.agriledger.module.system.convert.dept.DeptConvert;
import com.agsccc.agriledger.module.system.dal.dataobject.dept.PostDO;
import com.agsccc.agriledger.module.system.service.dept.PostService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.Collection;
import java.util.List;

/**
 * 岗位 API 实现类
 *
 * @author 农E链源码
 */
@Service
public class PostApiImpl implements PostApi {

    @Resource
    private PostService postService;

    @Override
    public void validPostList(Collection<Long> ids) {
        postService.validatePostList(ids);
    }

    @Override
    public List<PostRespDTO> getPostList(Collection<Long> ids) {
        List<PostDO> list = postService.getPostList(ids);
        return DeptConvert.INSTANCE.convertPostRespDTOList(list);
    }

}
