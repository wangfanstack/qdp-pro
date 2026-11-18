package com.agsccc.agriledger.module.system.service.account;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.common.util.collection.CollectionUtils;
import com.agsccc.agriledger.framework.datapermission.core.util.DataPermissionUtils;
import com.agsccc.agriledger.framework.tenant.core.util.TenantUtils;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;
import com.agsccc.agriledger.module.system.dal.mysql.account.SystemAccountMapper;
import com.agsccc.agriledger.module.system.dal.mysql.account.SystemAccountUserRelMapper;
import com.agsccc.agriledger.module.system.service.tenant.TenantService;
import com.agsccc.agriledger.module.system.service.user.AdminUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_DISABLE;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_TENANT_NOT_REL;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_USER_NOT_EXISTS;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_USER_TENANT_NOT_MATCH;

/**
 * 全局账号 Service 实现类
 *
 * @author 农E链源码
 */
@Service
@Validated
@Slf4j
public class SystemAccountServiceImpl implements SystemAccountService {

    @Resource
    private SystemAccountMapper systemAccountMapper;

    @Resource
    private SystemAccountUserRelMapper systemAccountUserRelMapper;

    @Resource
    private TenantService tenantService;

    @Resource
    private AdminUserService userService;

    @Override
    public SystemAccountDO getOrCreateAccountByMobile(String mobile) {
        // 1. 查询已有账号
        SystemAccountDO account = systemAccountMapper.selectByMobile(mobile);
        if (account != null) {
            return account;
        }
        // 2. 不存在则创建；同一手机号并发注册由 uk_mobile 唯一键兜底，冲突后重查
        try {
            account = SystemAccountDO.builder().mobile(mobile)
                    .status(CommonStatusEnum.ENABLE.getStatus()).build();
            systemAccountMapper.insert(account);
            return account;
        } catch (DuplicateKeyException ex) {
            account = systemAccountMapper.selectByMobile(mobile);
            if (account != null) {
                log.info("[getOrCreateAccountByMobile][手机号({}) 并发注册，复用已有账号({})]",
                        mobile, account.getId());
                return account;
            }
            throw ex;
        }
    }

    @Override
    public void validateAccountEnabled(SystemAccountDO account) {
        if (account == null || CommonStatusEnum.isDisable(account.getStatus())) {
            throw exception(ACCOUNT_DISABLE);
        }
    }

    @Override
    public void bindAccountTenant(Long accountId, Long tenantId, Long systemUserId) {
        SystemAccountUserRelDO rel = systemAccountUserRelMapper.selectByAccountIdAndTenantId(accountId, tenantId);
        if (rel == null) {
            try {
                systemAccountUserRelMapper.insert(SystemAccountUserRelDO.builder()
                        .accountId(accountId).tenantId(tenantId).systemUserId(systemUserId)
                        .status(CommonStatusEnum.ENABLE.getStatus()).build());
                return;
            } catch (DuplicateKeyException ex) {
                // 并发绑定兜底：重查已存在关联
                rel = systemAccountUserRelMapper.selectByAccountIdAndTenantId(accountId, tenantId);
                if (rel == null) {
                    throw ex;
                }
            }
        }
        // 已存在（含曾停用）：刷新为有效并更新绑定用户（幂等）
        systemAccountUserRelMapper.updateById(SystemAccountUserRelDO.builder()
                .id(rel.getId()).systemUserId(systemUserId)
                .status(CommonStatusEnum.ENABLE.getStatus()).build());
    }

    @Override
    public Long getOwnerAccountIdByTenantId(Long tenantId) {
        SystemAccountUserRelDO rel = systemAccountUserRelMapper.selectFirstByTenantId(tenantId);
        return rel != null ? rel.getAccountId() : null;
    }

    @Override
    public AdminUserDO validateSwitchContext(Long accountId, Long targetTenantId) {
        // 1. 校验账号与目标租户的有效关联（不存在/已停用均视为无有效关联）
        getValidRel(accountId, targetTenantId);
        // 2. 校验目标租户状态（不存在/停用/过期由 TenantService 抛出）
        tenantService.validTenant(targetTenantId);
        // 3. 在目标租户上下文中加载管理用户，并校验归属与状态
        //    - system_users 为租户表，查询会被拼接 tenant_id 条件，必须切到目标租户上下文；
        //    - 身份加载不属于数据权限范围（如 SELF 范围会拼接 user_id 条件导致跨租户目标用户被过滤），须忽略数据权限
        SystemAccountUserRelDO rel = systemAccountUserRelMapper.selectByAccountIdAndTenantId(accountId, targetTenantId);
        return TenantUtils.execute(targetTenantId, () -> {
            AdminUserDO user = DataPermissionUtils.executeIgnore(() -> userService.getUser(rel.getSystemUserId()));
            if (user == null) {
                throw exception(ACCOUNT_USER_NOT_EXISTS);
            }
            if (!Objects.equals(user.getTenantId(), targetTenantId)) {
                throw exception(ACCOUNT_USER_TENANT_NOT_MATCH);
            }
            if (CommonStatusEnum.isDisable(user.getStatus())) {
                throw exception(ACCOUNT_USER_NOT_EXISTS);
            }
            return user;
        });
    }

    @Override
    public SystemAccountUserRelDO getValidRel(Long accountId, Long tenantId) {
        SystemAccountUserRelDO rel = systemAccountUserRelMapper.selectByAccountIdAndTenantId(accountId, tenantId);
        if (rel == null || CommonStatusEnum.isDisable(rel.getStatus())) {
            throw exception(ACCOUNT_TENANT_NOT_REL);
        }
        return rel;
    }

    @Override
    public List<SystemAccountUserRelDO> getValidRelListByAccountId(Long accountId) {
        List<SystemAccountUserRelDO> rels = systemAccountUserRelMapper.selectListByAccountId(accountId);
        return CollectionUtils.filterList(rels,
                rel -> CommonStatusEnum.ENABLE.getStatus().equals(rel.getStatus()));
    }

    @Override
    public SystemAccountUserRelDO getRelBySystemUserId(Long systemUserId) {
        return systemAccountUserRelMapper.selectBySystemUserId(systemUserId);
    }

    @Override
    public List<TenantDO> getAccessibleTenantList(Long accountId) {
        List<SystemAccountUserRelDO> rels = getValidRelListByAccountId(accountId);
        return convertTenantList(rels);
    }

    @Override
    public List<TenantDO> getAccessibleTenantListByUserId(Long systemUserId) {
        SystemAccountUserRelDO rel = getRelBySystemUserId(systemUserId);
        if (rel == null) {
            return Collections.emptyList();
        }
        return getAccessibleTenantList(rel.getAccountId());
    }

    /**
     * 关联列表转换为租户列表：过滤已删除的租户（含已停用租户，由前端置灰展示）
     */
    private List<TenantDO> convertTenantList(List<SystemAccountUserRelDO> rels) {
        if (rels == null || rels.isEmpty()) {
            return Collections.emptyList();
        }
        return CollectionUtils.filterList(CollectionUtils.convertList(rels,
                rel -> tenantService.getTenant(rel.getTenantId())), Objects::nonNull);
    }

}
