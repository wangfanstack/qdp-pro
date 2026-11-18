package com.agsccc.agriledger.module.system.service.auth;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.common.enums.UserTypeEnum;
import com.agsccc.agriledger.framework.security.core.LoginUser;
import com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils;
import com.agsccc.agriledger.framework.common.exception.ServiceException;
import com.agsccc.agriledger.framework.test.core.ut.BaseMockitoUnitTest;
import com.agsccc.agriledger.module.system.api.sms.SmsCodeApi;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSwitchTenantReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import com.agsccc.agriledger.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;
import com.agsccc.agriledger.module.system.enums.oauth2.OAuth2ClientConstants;
import com.agsccc.agriledger.module.system.service.account.SystemAccountService;
import com.agsccc.agriledger.module.system.service.logger.LoginLogService;
import com.agsccc.agriledger.module.system.service.oauth2.OAuth2TokenService;
import com.agsccc.agriledger.module.system.service.tenant.TenantService;
import com.agsccc.agriledger.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static com.agsccc.agriledger.framework.test.core.util.AssertUtils.assertServiceException;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_NOT_EXISTS;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_TENANT_NOT_REL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AppAuthServiceImpl} 的单元测试（重点覆盖切换租户）
 *
 * @author 农E链源码
 */
public class AppAuthServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppAuthServiceImpl appAuthService;

    @Mock
    private SmsCodeApi smsCodeApi;
    @Mock
    private SystemAccountService systemAccountService;
    @Mock
    private AdminUserService userService;
    @Mock
    private TenantService tenantService;
    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private LoginLogService loginLogService;
    @Mock
    private com.agsccc.agriledger.module.system.dal.redis.auth.AuthTicketRedisDAO authTicketRedisDAO;

    private MockedStatic<SecurityFrameworkUtils> securityFrameworkUtilsMock;

    private static final String TOKEN = "old-access-token";

    @BeforeEach
    public void setUpStatic() {
        securityFrameworkUtilsMock = Mockito.mockStatic(SecurityFrameworkUtils.class);
        // 注入 @Value 配置字段（Mockito 不会注入）
        org.springframework.test.util.ReflectionTestUtils.setField(appAuthService, "ticketValidity", java.time.Duration.ofMinutes(30));
    }

    @AfterEach
    public void tearDownStatic() {
        securityFrameworkUtilsMock.close();
    }

    private LoginUser buildLoginUser(Long userId, Long accountId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setAccountId(accountId);
        loginUser.setUserType(UserTypeEnum.ADMIN.getValue());
        loginUser.setTenantId(1L);
        return loginUser;
    }

    private AdminUserDO buildUser(Long id, Long tenantId) {
        AdminUserDO user = AdminUserDO.builder().id(id).status(CommonStatusEnum.ENABLE.getStatus()).build();
        user.setTenantId(tenantId);
        return user;
    }

    private OAuth2AccessTokenDO buildAccessToken(Long userId, Long tenantId) {
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO()
                .setUserId(userId).setUserType(UserTypeEnum.ADMIN.getValue())
                .setAccessToken("new-access-token").setRefreshToken("new-refresh-token")
                .setExpiresTime(LocalDateTime.now().plusHours(2));
        accessTokenDO.setTenantId(tenantId); // TenantBaseDO 父类字段，链式 setter 返回父类型，需单独设置
        return accessTokenDO;
    }

    // ========== smsLogin：全局账号级（两段式第一段，颁发 ticket） ==========

    @Test
    public void testSmsLogin_issueTicket() {
        // mock：验证码消费成功，账号获取或创建成功
        com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO account =
                com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO.builder()
                        .id(7L).mobile("13900009999")
                        .status(com.agsccc.agriledger.framework.common.enums.CommonStatusEnum.ENABLE.getStatus())
                        .build();
        when(systemAccountService.getOrCreateAccountByMobile("13900009999")).thenReturn(account);

        // 调用
        com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthPreLoginRespVO respVO =
                appAuthService.smsLogin(com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsLoginReqVO
                        .builder().mobile("13900009999").code("102410").build());

        // 断言：消费验证码、颁发 ticket、不签发任何令牌、不触碰租户/关联
        org.junit.jupiter.api.Assertions.assertEquals(7L, respVO.getAccountId());
        org.junit.jupiter.api.Assertions.assertNotNull(respVO.getTicket());
        verify(smsCodeApi).useSmsCode(any());
        verify(authTicketRedisDAO).create(any(), eq(7L), any());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
        verify(systemAccountService, never()).getValidRelListByAccountId(anyLong());
    }

    // ========== enterMerchant：凭 ticket 换正式令牌 ==========

    @Test
    public void testEnterMerchant_success() {
        // mock：ticket 有效，三重校验通过，签发成功
        when(authTicketRedisDAO.getAccountId("t-001")).thenReturn(7L);
        when(systemAccountService.validateSwitchContext(7L, 2L)).thenReturn(buildUser(20L, 2L));
        when(oauth2TokenService.createAccessToken(eq(20L), eq(UserTypeEnum.ADMIN.getValue()),
                eq("app"), eq(null))).thenReturn(buildAccessToken(20L, 2L));

        com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO reqVO =
                new com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO();
        reqVO.setTenantId(2L);
        var respVO = appAuthService.enterMerchant("t-001", reqVO);

        // 断言：换取正式令牌，ticket 一次性消费
        assertEquals(7L, respVO.getAccountId());
        assertEquals(20L, respVO.getUserId());
        assertEquals(2L, respVO.getTenantId());
        verify(authTicketRedisDAO).delete("t-001");
    }

    @Test
    public void testEnterMerchant_invalidTicket() {
        // mock：ticket 无效/已过期/已消费
        when(authTicketRedisDAO.getAccountId("t-bad")).thenReturn(null);
        com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO reqVO =
                new com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO();
        reqVO.setTenantId(2L);
        assertServiceException(() -> appAuthService.enterMerchant("t-bad", reqVO),
                com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.AUTH_TICKET_INVALID);
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
    }

    @Test
    public void testEnterMerchant_noRel() {
        // mock：ticket 有效但账号与目标租户无有效关联（校验失败，不消费 ticket）
        when(authTicketRedisDAO.getAccountId("t-002")).thenReturn(7L);
        doThrow(new ServiceException(ACCOUNT_TENANT_NOT_REL))
                .when(systemAccountService).validateSwitchContext(7L, 3L);
        com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO reqVO =
                new com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO();
        reqVO.setTenantId(3L);
        assertServiceException(() -> appAuthService.enterMerchant("t-002", reqVO), ACCOUNT_TENANT_NOT_REL);
        verify(authTicketRedisDAO, never()).delete("t-002");
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
    }

    // ========== switchTenant ==========

    @Test
    public void testSwitchTenant_success() {
        // mock：token 已携带 accountId；三重校验通过；重签成功
        securityFrameworkUtilsMock.when(SecurityFrameworkUtils::getLoginUser).thenReturn(buildLoginUser(20L, 1L));
        when(systemAccountService.validateSwitchContext(1L, 2L)).thenReturn(buildUser(20L, 2L));
        when(oauth2TokenService.createAccessToken(20L, UserTypeEnum.ADMIN.getValue(),
                OAuth2ClientConstants.CLIENT_ID_APP, null)).thenReturn(buildAccessToken(20L, 2L));

        // 调用
        AppAuthLoginRespVO respVO = appAuthService.switchTenant(TOKEN, new AppAuthSwitchTenantReqVO().setTenantId(2L));
        // 断言：新令牌为目标租户上下文，并携带 accountId
        assertEquals(1L, respVO.getAccountId());
        assertEquals(20L, respVO.getUserId());
        assertEquals(2L, respVO.getTenantId());
        assertEquals("new-access-token", respVO.getAccessToken());
        // 断言：先吊销旧令牌、后签发新令牌（避免新旧租户令牌并存）
        InOrder inOrder = Mockito.inOrder(oauth2TokenService);
        inOrder.verify(oauth2TokenService).removeAccessToken(TOKEN);
        inOrder.verify(oauth2TokenService).createAccessToken(eq(20L), eq(UserTypeEnum.ADMIN.getValue()),
                eq(OAuth2ClientConstants.CLIENT_ID_APP), eq(null));
    }

    @Test
    public void testSwitchTenant_fallbackResolveAccountId() {
        // mock：旧令牌 userInfo 不含 accountId，按用户反查关联获得
        securityFrameworkUtilsMock.when(SecurityFrameworkUtils::getLoginUser).thenReturn(buildLoginUser(20L, null));
        when(systemAccountService.getRelBySystemUserId(20L))
                .thenReturn(SystemAccountUserRelDO.builder().id(10L).accountId(5L).tenantId(1L)
                        .systemUserId(20L).status(CommonStatusEnum.ENABLE.getStatus()).build());
        when(systemAccountService.validateSwitchContext(5L, 2L)).thenReturn(buildUser(20L, 2L));
        when(oauth2TokenService.createAccessToken(anyLong(), any(), any(), any())).thenReturn(buildAccessToken(20L, 2L));

        // 调用 + 断言
        AppAuthLoginRespVO respVO = appAuthService.switchTenant(TOKEN, new AppAuthSwitchTenantReqVO().setTenantId(2L));
        assertEquals(5L, respVO.getAccountId());
    }

    @Test
    public void testSwitchTenant_noAccount() {
        // mock：用户无全局账号（历史存量用户）
        securityFrameworkUtilsMock.when(SecurityFrameworkUtils::getLoginUser).thenReturn(buildLoginUser(20L, null));
        when(systemAccountService.getRelBySystemUserId(20L)).thenReturn(null);

        // 调用 + 断言：直接拒绝，不触碰任何令牌
        assertServiceException(() ->
                appAuthService.switchTenant(TOKEN, new AppAuthSwitchTenantReqVO().setTenantId(2L)), ACCOUNT_NOT_EXISTS);
        verify(oauth2TokenService, never()).removeAccessToken(any());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
    }

    @Test
    public void testSwitchTenant_contextInvalid() {
        // mock：账号与目标租户无有效关联
        securityFrameworkUtilsMock.when(SecurityFrameworkUtils::getLoginUser).thenReturn(buildLoginUser(20L, 1L));
        doThrow(new com.agsccc.agriledger.framework.common.exception.ServiceException(ACCOUNT_TENANT_NOT_REL))
                .when(systemAccountService).validateSwitchContext(1L, 2L);

        // 调用 + 断言：前置校验失败直接抛出，不触碰任何令牌
        assertServiceException(() ->
                appAuthService.switchTenant(TOKEN, new AppAuthSwitchTenantReqVO().setTenantId(2L)), ACCOUNT_TENANT_NOT_REL);
        verify(oauth2TokenService, never()).removeAccessToken(any());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
    }

}
