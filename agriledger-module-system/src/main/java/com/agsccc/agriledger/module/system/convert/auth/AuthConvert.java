package com.agsccc.agriledger.module.system.convert.auth;

import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.agsccc.agriledger.module.system.api.social.dto.SocialUserBindReqDTO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthAccessibleTenantRespVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthSmsSendReqVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.agsccc.agriledger.module.system.dal.dataobject.permission.MenuDO;
import com.agsccc.agriledger.module.system.dal.dataobject.permission.RoleDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;
import com.agsccc.agriledger.module.system.enums.permission.MenuTypeEnum;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.agsccc.agriledger.framework.common.util.collection.CollectionUtils.convertSet;
import static com.agsccc.agriledger.framework.common.util.collection.CollectionUtils.filterList;
import static com.agsccc.agriledger.module.system.dal.dataobject.permission.MenuDO.ID_ROOT;

@Mapper
public interface AuthConvert {

    AuthConvert INSTANCE = Mappers.getMapper(AuthConvert.class);

    AuthPermissionInfoRespVO.UserVO convert(AdminUserDO bean);

    AuthPermissionInfoRespVO.MenuVO convert(MenuDO bean);

    AuthLoginRespVO convertToLoginRespVO(OAuth2AccessTokenDO bean);

    @Mapping(source = "id", target = "tenantId")
    AuthAccessibleTenantRespVO convert(TenantDO bean);

    List<AuthAccessibleTenantRespVO> convertList(List<TenantDO> list);

    default AuthPermissionInfoRespVO convert(AdminUserDO user, List<RoleDO> roleList, List<MenuDO> menuList) {
        return AuthPermissionInfoRespVO.builder()
                .user(convert(user))
                .roles(convertSet(roleList, RoleDO::getCode))
                // 权限标识信息
                .permissions(convertSet(menuList, MenuDO::getPermission))
                // 菜单树
                .menus(buildMenuTree(menuList))
                .build();
    }

    /**
     * 将菜单列表，构建成菜单树
     *
     * @param menuList 菜单列表
     * @return 菜单树
     */
    default List<AuthPermissionInfoRespVO.MenuVO> buildMenuTree(List<MenuDO> menuList) {
        if (menuList == null || menuList.isEmpty()) {
            return Collections.emptyList();
        }
        // 移除按钮
        menuList.removeIf(menu -> menu.getType().equals(MenuTypeEnum.BUTTON.getType()));
        // 排序，保证菜单的有序性
        menuList.sort(Comparator.comparing(MenuDO::getSort));

        // 构建菜单树
        // 使用 LinkedHashMap 的原因，是为了排序 。实际也可以用 Stream API ，就是太丑了。
        Map<Long, AuthPermissionInfoRespVO.MenuVO> treeNodeMap = new LinkedHashMap<>();
        menuList.forEach(menu -> treeNodeMap.put(menu.getId(), convert(menu)));
        // 处理父子关系
        treeNodeMap.values().stream().filter(node -> !Objects.equals(node.getParentId(), ID_ROOT)).forEach(childNode -> {
            // 获得父节点
            AuthPermissionInfoRespVO.MenuVO parentNode = treeNodeMap.get(childNode.getParentId());
            if (parentNode == null) {
                LoggerFactory.getLogger(getClass()).error("[buildRouterTree][resource({}) 找不到父资源({})]",
                        childNode.getId(), childNode.getParentId());
                return;
            }
            // 将自己添加到父节点中
            if (parentNode.getChildren() == null) {
                parentNode.setChildren(new ArrayList<>());
            }
            parentNode.getChildren().add(childNode);
        });
        // 获得到所有的根节点
        return filterList(treeNodeMap.values(), node -> ID_ROOT.equals(node.getParentId()));
    }

    SocialUserBindReqDTO convert(Long userId, Integer userType, AuthSocialLoginReqVO reqVO);

    SmsCodeSendReqDTO convert(AuthSmsSendReqVO reqVO);

    SmsCodeUseReqDTO convert(AuthSmsLoginReqVO reqVO, Integer scene, String usedIp);

}
