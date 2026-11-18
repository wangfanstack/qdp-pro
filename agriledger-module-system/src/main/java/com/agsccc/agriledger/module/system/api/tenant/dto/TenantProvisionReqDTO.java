package com.agsccc.agriledger.module.system.api.tenant.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 商户租户开通（聚合）Request DTO
 *
 * @author 农E链源码
 */
@Data
@Accessors(chain = true)
public class TenantProvisionReqDTO implements Serializable {

    /**
     * 租户名称（需唯一，由调用方保证/重试）
     */
    private String tenantName;
    /**
     * 租户套餐编号
     */
    private Long packageId;
    /**
     * 账号配额（租户内可创建用户数上限）
     */
    private Integer accountCount = 10;
    /**
     * 联系人姓名
     */
    private String contactName;
    /**
     * 联系手机号
     */
    private String contactMobile;
    /**
     * 租户管理员登录账号（建议使用手机号）
     */
    private String username;
    /**
     * 租户管理员初始密码（商户场景建议传随机值，用户走短信登录）
     */
    private String password;
    /**
     * 租户管理员昵称
     */
    private String nickname;
    /**
     * 绑定的全局账号编号（system_account.id）
     */
    private Long accountId;

}
