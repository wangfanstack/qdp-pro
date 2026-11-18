package com.agsccc.agriledger.module.system.service.account;

import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;

import java.util.List;

/**
 * 全局账号 Service 接口
 *
 * @author 农E链源码
 */
public interface SystemAccountService {

    /**
     * 根据手机号获取全局账号；不存在则创建（并发下由 uk_mobile 唯一键兜底）
     *
     * @param mobile 手机号
     * @return 全局账号
     */
    SystemAccountDO getOrCreateAccountByMobile(String mobile);

    /**
     * 校验切换租户的目标上下文：账号-目标租户有效关联 → 目标租户状态 → 目标租户管理用户（存在/归属/状态），
     * 全部通过后返回该管理用户；任一不满足直接抛出业务异常（切换前置校验，token 操作由认证层执行）
     *
     * @param accountId     全局账号编号
     * @param targetTenantId 目标租户编号
     * @return 目标租户下的管理用户（已启用）
     */
    AdminUserDO validateSwitchContext(Long accountId, Long targetTenantId);

    /**
     * 绑定全局账号与租户管理用户（商户开通流程使用，幂等）：
     * 关联不存在则创建（uk_account_tenant 并发兜底）；已存在（含曾停用）则刷新为有效
     *
     * @param accountId     全局账号编号
     * @param tenantId      租户编号
     * @param systemUserId  租户管理用户编号
     */
    void bindAccountTenant(Long accountId, Long tenantId, Long systemUserId);

    /**
     * 获得租户最早关联（开通者）的全局账号编号
     *
     * @param tenantId 租户编号
     * @return 全局账号编号；租户无关联返回 null
     */
    Long getOwnerAccountIdByTenantId(Long tenantId);

    /**
     * 校验全局账号处于开启状态
     *
     * @param account 全局账号
     */
    void validateAccountEnabled(SystemAccountDO account);

    /**
     * 获得账号在指定租户的有效关联（不存在或已停用时抛出异常）
     *
     * @param accountId 全局账号编号
     * @param tenantId  租户编号
     * @return 关联关系
     */
    SystemAccountUserRelDO getValidRel(Long accountId, Long tenantId);

    /**
     * 获得账号的全部有效关联（按关联编号升序）
     *
     * @param accountId 全局账号编号
     * @return 关联关系列表
     */
    List<SystemAccountUserRelDO> getValidRelListByAccountId(Long accountId);

    /**
     * 根据租户管理用户编号获得其所属关联（一个用户仅被一个全局账号绑定）
     *
     * @param systemUserId 管理用户编号（system_users.id）
     * @return 关联关系，不存在时返回 null
     */
    SystemAccountUserRelDO getRelBySystemUserId(Long systemUserId);

    /**
     * 获得账号可访问的租户列表（有效关联 + 租户存在；含停用租户，由前端置灰展示）
     *
     * @param accountId 全局账号编号
     * @return 租户列表
     */
    List<TenantDO> getAccessibleTenantList(Long accountId);

    /**
     * 根据租户管理用户编号，获得其全局账号可访问的租户列表
     *
     * @param systemUserId 管理用户编号（system_users.id）
     * @return 租户列表；用户无全局账号时返回空列表
     */
    List<TenantDO> getAccessibleTenantListByUserId(Long systemUserId);

}
