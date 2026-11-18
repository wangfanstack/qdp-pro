package com.agsccc.agriledger.module.system.service.account;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.common.exception.ServiceException;
import com.agsccc.agriledger.framework.test.core.ut.BaseMockitoUnitTest;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;
import com.agsccc.agriledger.module.system.dal.mysql.account.SystemAccountMapper;
import com.agsccc.agriledger.module.system.dal.mysql.account.SystemAccountUserRelMapper;
import com.agsccc.agriledger.module.system.service.tenant.TenantService;
import com.agsccc.agriledger.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.agsccc.agriledger.framework.test.core.util.AssertUtils.assertServiceException;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_DISABLE;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_TENANT_NOT_REL;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_USER_NOT_EXISTS;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_USER_TENANT_NOT_MATCH;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.TENANT_DISABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SystemAccountServiceImpl} 的单元测试
 *
 * @author 农E链源码
 */
public class SystemAccountServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private SystemAccountServiceImpl systemAccountService;

    @Mock
    private SystemAccountMapper systemAccountMapper;
    @Mock
    private SystemAccountUserRelMapper systemAccountUserRelMapper;
    @Mock
    private TenantService tenantService;
    @Mock
    private AdminUserService userService;

    private static final String MOBILE = "15601691300";

    private SystemAccountDO buildAccount(Long id, Integer status) {
        return SystemAccountDO.builder().id(id).mobile(MOBILE).status(status).build();
    }

    private SystemAccountUserRelDO buildRel(Long id, Long accountId, Long tenantId, Long systemUserId, Integer status) {
        return SystemAccountUserRelDO.builder().id(id).accountId(accountId).tenantId(tenantId)
                .systemUserId(systemUserId).status(status).build();
    }

    // ========== getOrCreateAccountByMobile ==========

    @Test
    public void testGetOrCreateAccountByMobile_exists() {
        // mock：已有账号
        SystemAccountDO account = buildAccount(1L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountMapper.selectByMobile(MOBILE)).thenReturn(account);

        // 调用
        SystemAccountDO result = systemAccountService.getOrCreateAccountByMobile(MOBILE);
        // 断言：直接返回已有账号，不插入
        assertSame(account, result);
        verify(systemAccountMapper, never()).insert(any(SystemAccountDO.class));
    }

    @Test
    public void testGetOrCreateAccountByMobile_insert() {
        // mock：无已有账号，插入成功
        when(systemAccountMapper.selectByMobile(MOBILE)).thenReturn(null);
        when(systemAccountMapper.insert(any(SystemAccountDO.class))).thenReturn(1);

        // 调用
        SystemAccountDO result = systemAccountService.getOrCreateAccountByMobile(MOBILE);
        // 断言：返回新建账号（开启状态）
        assertEquals(MOBILE, result.getMobile());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), result.getStatus());
        verify(systemAccountMapper).insert(any(SystemAccountDO.class));
    }

    @Test
    public void testGetOrCreateAccountByMobile_duplicate() {
        // mock：首查为空，插入触发唯一键冲突，重查返回已有账号（并发注册场景）
        when(systemAccountMapper.selectByMobile(MOBILE)).thenReturn(null, buildAccount(2L, CommonStatusEnum.ENABLE.getStatus()));
        when(systemAccountMapper.insert(any(SystemAccountDO.class))).thenThrow(new DuplicateKeyException("uk_mobile"));

        // 调用
        SystemAccountDO result = systemAccountService.getOrCreateAccountByMobile(MOBILE);
        // 断言：复用并发创建的账号
        assertEquals(2L, result.getId());
    }

    @Test
    public void testGetOrCreateAccountByMobile_duplicateButMissing() {
        // mock：冲突后重查仍为空（异常场景），应原样抛出
        when(systemAccountMapper.selectByMobile(MOBILE)).thenReturn(null);
        when(systemAccountMapper.insert(any(SystemAccountDO.class))).thenThrow(new DuplicateKeyException("uk_mobile"));

        // 调用 + 断言
        assertThrows(DuplicateKeyException.class,
                () -> systemAccountService.getOrCreateAccountByMobile(MOBILE));
    }

    // ========== validateAccountEnabled ==========

    @Test
    public void testValidateAccountEnabled_disable() {
        // 调用 + 断言
        assertServiceException(() -> systemAccountService.validateAccountEnabled(
                buildAccount(1L, CommonStatusEnum.DISABLE.getStatus())), ACCOUNT_DISABLE);
    }

    @Test
    public void testValidateAccountEnabled_null() {
        // 调用 + 断言
        assertServiceException(() -> systemAccountService.validateAccountEnabled(null), ACCOUNT_DISABLE);
    }

    // ========== getValidRel ==========

    @Test
    public void testGetValidRel_notExists() {
        // mock
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(null);
        // 调用 + 断言
        assertServiceException(() -> systemAccountService.getValidRel(1L, 2L), ACCOUNT_TENANT_NOT_REL);
    }

    @Test
    public void testGetValidRel_disabled() {
        // mock：关联已停用
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L))
                .thenReturn(buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.DISABLE.getStatus()));
        // 调用 + 断言：停用等价于无有效关联
        assertServiceException(() -> systemAccountService.getValidRel(1L, 2L), ACCOUNT_TENANT_NOT_REL);
    }

    @Test
    public void testGetValidRel_success() {
        // mock
        SystemAccountUserRelDO rel = buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(rel);
        // 调用 + 断言
        assertSame(rel, systemAccountService.getValidRel(1L, 2L));
    }

    // ========== getAccessibleTenantList / ByUserId ==========
    @Test
    public void testGetAccessibleTenantList_filter() {
        // mock：租户 1 有效关联、租户 2 停用关联、租户 3 有效关联但租户已删除
        SystemAccountUserRelDO rel1 = buildRel(10L, 1L, 101L, 201L, CommonStatusEnum.ENABLE.getStatus());
        SystemAccountUserRelDO rel2 = buildRel(11L, 1L, 102L, 202L, CommonStatusEnum.DISABLE.getStatus());
        SystemAccountUserRelDO rel3 = buildRel(12L, 1L, 103L, 203L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectListByAccountId(1L)).thenReturn(Arrays.asList(rel1, rel2, rel3));
        when(tenantService.getTenant(101L)).thenReturn(new TenantDO().setId(101L).setName("租户一"));
        when(tenantService.getTenant(103L)).thenReturn(null); // 已删除的租户

        // 调用
        List<TenantDO> result = systemAccountService.getAccessibleTenantList(1L);
        // 断言：仅保留有效关联且租户存在的记录
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getId());
    }

    @Test
    public void testGetAccessibleTenantListByUserId_noRel() {
        // mock：用户未关联全局账号（历史存量用户）
        when(systemAccountUserRelMapper.selectBySystemUserId(20L)).thenReturn(null);
        // 调用 + 断言：返回空列表而非报错
        assertTrue(systemAccountService.getAccessibleTenantListByUserId(20L).isEmpty());
    }

    @Test
    public void testGetAccessibleTenantListByUserId_success() {
        // mock
        when(systemAccountUserRelMapper.selectBySystemUserId(20L))
                .thenReturn(buildRel(10L, 1L, 101L, 20L, CommonStatusEnum.ENABLE.getStatus()));
        when(systemAccountUserRelMapper.selectListByAccountId(1L))
                .thenReturn(Collections.singletonList(buildRel(10L, 1L, 101L, 20L, CommonStatusEnum.ENABLE.getStatus())));
        when(tenantService.getTenant(101L)).thenReturn(new TenantDO().setId(101L));

        // 调用 + 断言
        assertEquals(1, systemAccountService.getAccessibleTenantListByUserId(20L).size());
    }

    // ========== validateSwitchContext（切换租户三重校验） ==========

    private AdminUserDO buildUser(Long id, Long tenantId, Integer status) {
        AdminUserDO user = AdminUserDO.builder().id(id).status(status).build();
        user.setTenantId(tenantId); // BaseDO 系父类字段，链式 setter 返回父类型，需单独设置
        return user;
    }

    @Test
    public void testValidateSwitchContext_success() {
        // mock：关联有效 + 租户有效 + 用户正常
        SystemAccountUserRelDO rel = buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(rel);
        doNothing().when(tenantService).validTenant(2L);
        when(userService.getUser(20L)).thenReturn(buildUser(20L, 2L, CommonStatusEnum.ENABLE.getStatus()));

        // 调用 + 断言
        AdminUserDO user = systemAccountService.validateSwitchContext(1L, 2L);
        assertEquals(20L, user.getId());
        assertEquals(2L, user.getTenantId());
    }

    @Test
    public void testValidateSwitchContext_relInvalid() {
        // mock：目标租户无有效关联
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 3L)).thenReturn(null);
        // 调用 + 断言：直接拒绝，不再校验租户/用户
        assertServiceException(() -> systemAccountService.validateSwitchContext(1L, 3L), ACCOUNT_TENANT_NOT_REL);
        verify(tenantService, never()).validTenant(anyLong());
    }

    @Test
    public void testValidateSwitchContext_tenantInvalid() {
        // mock：关联有效但目标租户停用（validTenant 抛出）
        SystemAccountUserRelDO rel = buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(rel);
        doThrow(new ServiceException(TENANT_DISABLE)).when(tenantService).validTenant(2L);
        // 调用 + 断言
        assertThrows(ServiceException.class, () -> systemAccountService.validateSwitchContext(1L, 2L));
        verify(userService, never()).getUser(anyLong());
    }

    @Test
    public void testValidateSwitchContext_userNotExists() {
        // mock：目标租户用户不存在（数据异常）
        SystemAccountUserRelDO rel = buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(rel);
        doNothing().when(tenantService).validTenant(2L);
        when(userService.getUser(20L)).thenReturn(null);
        // 调用 + 断言
        assertServiceException(() -> systemAccountService.validateSwitchContext(1L, 2L), ACCOUNT_USER_NOT_EXISTS);
    }

    @Test
    public void testValidateSwitchContext_userTenantNotMatch() {
        // mock：关联的用户归属与目标租户不一致
        SystemAccountUserRelDO rel = buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(rel);
        doNothing().when(tenantService).validTenant(2L);
        when(userService.getUser(20L)).thenReturn(buildUser(20L, 9L, CommonStatusEnum.ENABLE.getStatus()));
        // 调用 + 断言
        assertServiceException(() -> systemAccountService.validateSwitchContext(1L, 2L), ACCOUNT_USER_TENANT_NOT_MATCH);
    }

    @Test
    public void testValidateSwitchContext_userDisabled() {
        // mock：目标租户用户已停用
        SystemAccountUserRelDO rel = buildRel(10L, 1L, 2L, 20L, CommonStatusEnum.ENABLE.getStatus());
        when(systemAccountUserRelMapper.selectByAccountIdAndTenantId(1L, 2L)).thenReturn(rel);
        doNothing().when(tenantService).validTenant(2L);
        when(userService.getUser(20L)).thenReturn(buildUser(20L, 2L, CommonStatusEnum.DISABLE.getStatus()));
        // 调用 + 断言
        assertServiceException(() -> systemAccountService.validateSwitchContext(1L, 2L), ACCOUNT_USER_NOT_EXISTS);
    }

}
