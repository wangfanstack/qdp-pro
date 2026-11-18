package com.agsccc.agriledger.module.system.api.account;

import com.agsccc.agriledger.module.system.api.account.dto.SystemAccountRespDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 全局账号 API：供 merchant 等业务模块跨模块调用
 *
 * @author 农E链源码
 */
public interface SystemAccountApi {

    /**
     * 根据手机号获取全局账号；不存在则创建（并发下由 uk_mobile 唯一键兜底）
     *
     * @param mobile 手机号
     * @return 全局账号
     */
    SystemAccountRespDTO getOrCreateAccountByMobile(@Valid @NotEmpty String mobile);

    /**
     * 获得租户最早关联（开通者）的全局账号编号；租户无关联返回 null
     *
     * @param tenantId 租户编号
     * @return 全局账号编号
     */
    Long getOwnerAccountIdByTenantId(Long tenantId);

    /**
     * 获得全局账号的全部有效关联租户编号（按关联时间升序）
     *
     * @param accountId 全局账号编号
     * @return 租户编号列表
     */
    List<Long> getValidTenantIdsByAccountId(Long accountId);

    /**
     * 获得账号在指定租户的有效关联管理用户编号（无有效关联时抛出业务异常）
     *
     * @param accountId 全局账号编号
     * @param tenantId  租户编号
     * @return 租户管理用户编号（system_users.id）
     */
    Long getRelSystemUserId(Long accountId, Long tenantId);

}
