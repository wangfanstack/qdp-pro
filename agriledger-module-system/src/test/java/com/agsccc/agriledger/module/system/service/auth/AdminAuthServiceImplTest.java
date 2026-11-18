package com.agsccc.agriledger.module.system.service.auth;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.common.enums.UserTypeEnum;
import com.agsccc.agriledger.framework.common.exception.ServiceException;
import com.agsccc.agriledger.framework.test.core.ut.BaseMockitoUnitTest;
import com.agsccc.agriledger.module.system.api.sms.SmsCodeApi;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthEnterTenantReqVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthTicketRespVO;
import com.agsccc.agriledger.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;
import com.agsccc.agriledger.module.system.dal.redis.auth.AuthTicketRedisDAO;
import com.agsccc.agriledger.module.system.service.account.SystemAccountService;
import com.agsccc.agriledger.module.system.service.logger.LoginLogService;
import com.agsccc.agriledger.module.system.service.oauth2.OAuth2TokenService;
import com.agsccc.agriledger.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.agsccc.agriledger.framework.test.core.util.AssertUtils.assertServiceException;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_TENANT_NOT_REL;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.AUTH_TICKET_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminAuthServiceImpl} 的单元测试（重点覆盖 Web 两段式登录）
 *
 * @author 农E链源码
 */
public class AdminAuthServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AdminAuthServiceImpl authService;

    @Mock
    private SmsCodeApi smsCodeApi;
    @Mock
    private SystemAccountService systemAccountService;
    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private LoginLogService loginLogService;
    @Mock
    private AdminUserService userService;
    @Mock
    private AuthTicketRedisDAO authTicketRedisDAO;

    @BeforeEach
    public void setUp() {
        // 注入 @Value 配置字段（Mockito 不会注入）
        ReflectionTestUtils.setField(authService, "ticketValidity", Duration.ofMinutes(30));
    }

    private com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO buildSmsReqVO() {
        return com.agsccc.agriledger.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO.builder()
                .mobile("13900008888").code("102410").build();
    }

    private com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO buildAccount() {
        return com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO.builder()
                .id(8L).mobile("13900008888")
                .status(CommonStatusEnum.ENABLE.getStatus()).build();
    }

    private AdminUserDO buildUser(Long id, Long tenantId) {
        AdminUserDO user = AdminUserDO.builder().id(id).mobile("13900008888")
                .status(CommonStatusEnum.ENABLE.getStatus()).build();
        user.setTenantId(tenantId);
        return user;
    }

    // ========== smsLogin：全局账号级（两段式第一段） ==========

    @Test
    public void testSmsLogin_issueTicket() {
        // mock：验证码消费成功，账号获取或创建成功
        when(systemAccountService.getOrCreateAccountByMobile("13900008888")).thenReturn(buildAccount());

        AuthTicketRespVO respVO = authService.smsLogin(buildSmsReqVO());

        // 断言：颁发 ticket；不签发任何令牌、不触碰任何租户/关联
        assertEquals(8L, respVO.getAccountId());
        assertNotNull(respVO.getTicket());
        verify(smsCodeApi).useSmsCode(any());
        verify(authTicketRedisDAO).create(any(), eq(8L), any());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
        verify(systemAccountService, never()).getValidRel(anyLong(), anyLong());
    }

    // ========== enterTenant：凭 ticket 换 Web 正式令牌 ==========

    @Test
    public void testEnterTenant_success() {
        // mock：ticket 有效，三重校验通过，签发 default client 令牌
        when(authTicketRedisDAO.getAccountId("t-100")).thenReturn(8L);
        when(systemAccountService.validateSwitchContext(8L, 2L)).thenReturn(buildUser(30L, 2L));
        OAuth2AccessTokenDO accessTokenDO = new OAuth2AccessTokenDO()
                .setUserId(30L).setUserType(UserTypeEnum.ADMIN.getValue())
                .setAccessToken("web-token").setRefreshToken("web-refresh")
                .setExpiresTime(LocalDateTime.now().plusMinutes(30));
        accessTokenDO.setTenantId(2L);
        when(oauth2TokenService.createAccessToken(eq(30L), eq(UserTypeEnum.ADMIN.getValue()),
                eq("default"), eq(null))).thenReturn(accessTokenDO);

        AuthEnterTenantReqVO reqVO = new AuthEnterTenantReqVO();
        reqVO.setTenantId(2L);
        AuthLoginRespVO respVO = authService.enterTenant("t-100", reqVO);

        // 断言：换取 default client 令牌，ticket 一次性消费
        assertEquals(8L, respVO.getAccountId());
        assertEquals("web-token", respVO.getAccessToken());
        verify(authTicketRedisDAO).delete("t-100");
    }

    @Test
    public void testEnterTenant_invalidTicket() {
        when(authTicketRedisDAO.getAccountId("t-bad")).thenReturn(null);
        AuthEnterTenantReqVO reqVO = new AuthEnterTenantReqVO();
        reqVO.setTenantId(2L);
        assertServiceException(() -> authService.enterTenant("t-bad", reqVO), AUTH_TICKET_INVALID);
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
    }

    @Test
    public void testEnterTenant_noRel() {
        // mock：ticket 有效但账号与目标租户无有效关联（校验失败不消费 ticket、不签发令牌）
        when(authTicketRedisDAO.getAccountId("t-101")).thenReturn(8L);
        doThrow(new ServiceException(ACCOUNT_TENANT_NOT_REL))
                .when(systemAccountService).validateSwitchContext(8L, 3L);
        AuthEnterTenantReqVO reqVO = new AuthEnterTenantReqVO();
        reqVO.setTenantId(3L);
        assertServiceException(() -> authService.enterTenant("t-101", reqVO), ACCOUNT_TENANT_NOT_REL);
        verify(authTicketRedisDAO, never()).delete("t-101");
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), any(), any(), any());
    }

}
