package com.agsccc.agriledger.module.system.api.tenant;

import com.agsccc.agriledger.module.system.api.tenant.dto.TenantProvisionReqDTO;
import com.agsccc.agriledger.module.system.api.tenant.dto.TenantProvisionRespDTO;

import jakarta.validation.Valid;

/**
 * 商户租户开通（聚合）API：供 merchant 等业务模块跨模块调用
 * <p>
 * 一次调用完成：创建租户（含 tenant_admin 角色与套餐菜单）→ 创建租户管理员用户并分配角色
 * → 绑定全局账号与租户管理用户关联（system_account_user_rel）
 *
 * @author 农E链源码
 */
public interface TenantProvisionApi {

    /**
     * 开通商户租户（事务内聚合执行，任一步失败整体回滚）
     *
     * @param reqDTO 开通信息
     * @return 租户编号与租户管理员用户编号
     */
    TenantProvisionRespDTO provisionMerchantTenant(@Valid TenantProvisionReqDTO reqDTO);

}
