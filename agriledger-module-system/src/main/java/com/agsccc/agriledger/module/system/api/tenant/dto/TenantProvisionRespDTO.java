package com.agsccc.agriledger.module.system.api.tenant.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 商户租户开通（聚合）Response DTO
 *
 * @author 农E链源码
 */
@Data
@Accessors(chain = true)
public class TenantProvisionRespDTO implements Serializable {

    /**
     * 租户编号
     */
    private Long tenantId;
    /**
     * 租户管理员用户编号（system_users.id，已绑定全局账号）
     */
    private Long systemUserId;

}
